package io.github.basicmark.basicbarrels.block;

import io.github.basicmark.basicbarrels.BarrelException;
import io.github.basicmark.basicbarrels.BasicBarrels;
import io.github.basicmark.basicbarrels.managers.BarrelControllerManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BarrelController extends BarrelConduit {
    /*
     * Version of data offsets we're using.
     *
     * Chances are we might need to extend the loreText pre-amble
     * so we version our data so we can support backwards compatibility.
     */
    public static final int dataVersion = 1;

    /* Lore text offsets */
    public static final int versionOffset = 0;

    /* Version 1 offsets */
    public static final int typeOffset = 1;
    public static final String itemStackLoreName = "Barrel Controller";

    final static Set<BlockFace> connectableFaces = new HashSet<BlockFace>();
    final static Set<BlockFace> inputFaces = new HashSet<BlockFace>();
    private Set<Barrel> barrels = new HashSet<Barrel>();
    private BukkitTask clearHighlight = null;

    static {
        connectableFaces.add(BlockFace.NORTH);
        connectableFaces.add(BlockFace.EAST);
        connectableFaces.add(BlockFace.SOUTH);
        connectableFaces.add(BlockFace.WEST);
        connectableFaces.add(BlockFace.UP);
        connectableFaces.add(BlockFace.DOWN);

        inputFaces.add(BlockFace.NORTH);
        inputFaces.add(BlockFace.EAST);
        inputFaces.add(BlockFace.SOUTH);
        inputFaces.add(BlockFace.WEST);
        inputFaces.add(BlockFace.UP);
        inputFaces.add(BlockFace.DOWN);
    }

    public BarrelController(Block block, String fullName) {
        super(block, fullName);
        setController();
        BarrelControllerManager manager = BasicBarrels.getBarrelControllerManager();
        manager.registerController(this);
    }

    @Override
    public boolean load(ConfigurationSection config) {
        boolean ret;
        ret = super.load(config);
        if (ret) {
            BasicBarrels.logEvent(barrelControllerLogPrefix() + ": Barrel controller loaded");
        }
        return true;
    }

    @Override
    public void postChunkLoad(){
        this.conduitScan();
    }

    private String barrelControllerLogPrefix() {
        return getBukkitBlock().getWorld().getName() + "_" + getBukkitBlock().getX() + "_" + getBukkitBlock().getY() + "_" + getBukkitBlock().getZ();
    }

    public void removeBarrelController() {
        if (clearHighlight != null) {
            clearHighlight.cancel();
            for (Barrel barrel : barrels) {
                barrel.setHighlight(false);
            }
        }
        BarrelControllerManager manager = BasicBarrels.getBarrelControllerManager();
        manager.unregisterController(this);
    }

    public static String getName() {
        return itemStackLoreName;
    }

    static public Boolean itemStackIsBarrelController(ItemStack item) {
        if (item.getType() != Material.OBSERVER) {
            return false;
        }

        ItemMeta data = item.getItemMeta();
        if (data != null) {
            if (data.hasLore()) {
                List<String> loreText = data.getLore();
                if (loreText.size() >  BarrelController.typeOffset) {
                    if (loreText.get( BarrelController.typeOffset).equals(getName()))
                        return true;
                }
            }
        }

        return false;
    }

    @Override
    public void unload() {
        super.unload();
        BarrelControllerManager manager = BasicBarrels.getBarrelControllerManager();
        manager.unregisterController(this);
    }

    private void addBarrel(Barrel barrel) {
        barrels.add(barrel);
    }

    private void removeBarrel(Barrel barrel) {
        barrels.remove(barrel);
    }


    public void add(BarrelConduit conduit) {
        if (conduit instanceof Barrel) {
            Barrel barrel = (Barrel) conduit;
            addBarrel(barrel);
        }
    }

    public void remove(BarrelConduit conduit) {
        if (conduit instanceof Barrel) {
            Barrel barrel = (Barrel) conduit;
            removeBarrel(barrel);
        }
    }
    /*
     * Return all compatible barrels, this allows the insertion
     * code to handle the case where the first barrel is full but
     * other compatible barrels are available.
     */
    private Set<Barrel> findBarrels(ItemStack item) {
        Set<Barrel> barrelList = new HashSet<Barrel>();

        for (Barrel barrel : barrels) {
            if (barrel.matchsBarrelItem(item)) {
                barrelList.add(barrel);
            }
        }

        return barrelList;
    }

    private Set<Barrel> emptyBarrels() {
        Set<Barrel>  barrelList = new HashSet<Barrel>();

        for (Barrel barrel : barrels) {
            if (barrel.isEmpty()) {
                barrelList.add(barrel);
            }
        }

        return barrelList;
    }

    public void showConnectedBarrels() {
        if (clearHighlight != null) {
            /* Highlight already in progress */
            return;
        }

        for (Barrel barrel : barrels) {
            barrel.setHighlight(true);
        }
        final Plugin plugin = BarrelControllerManager.plugin;
        clearHighlight = new BukkitRunnable() {
            @Override
            public void run() {
                for (Barrel barrel : barrels) {
                    barrel.setHighlight(false);
                }
                clearHighlight = null;
            }
        }.runTaskLater(plugin, 20 * 10);
    }

    public void addItems(Inventory inventory, int firstSlot, int lastSlot, int addAmount) {
        /*
            Create a uniqueList itemstacks (material + data) from the inventory
            Foreach uniqueItemStack find all barrels which store that item type and not not empty
                moreToStore = true;
                Foreach barrel call addItemsFromInventory and check if it reports full
                    If not full
                        moreToStore = false
                        break
                if moreToStore
                    Create a list of barrels which are empty
                    Foreach barrel call addItemsFromInventory and check if it reports full
                        If not full
                            break
         */

        for (int slot = firstSlot; slot <= lastSlot; slot++) {
            ItemStack itemStack = inventory.getItem(slot);

            /* Check if there is an item in the slot and if it can be stored */
            if ((itemStack == null) || (itemStack.getType() == Material.AIR))
                continue;

            if (!Barrel.isStoreable(itemStack))
                continue;

            /*
                First try to find suitable barrel(s) and items to them first
                filling up the one barrel before moving to the next.
                If there are still more items after this then add the remaining
                items to empty barrels.
             */
            Set<Barrel> barrelList = findBarrels(itemStack);
            boolean full = true;
            for (Barrel barrel : barrelList) {
                try {
                    full = barrel.addItemsFromInventory(inventory, slot, lastSlot, addAmount);
                }
                catch (BarrelException e) {
                }
                if (!full) {
                    break;
                }
            }

            if (full) {
                barrelList = emptyBarrels();
                for (Barrel barrel : barrelList) {
                    try {
                        full = barrel.addItemsFromInventory(inventory, slot, lastSlot, addAmount);
                    }
                    catch (BarrelException e) {
                    }
                    if (!full) {
                        break;
                    }
                }
            }
        }
    }

    public void tick() {
        /*
         * Scan all faces within a single tick for blocks which contain
         * an inventory.
         * If a such a block is found scan its slots, starting from the
         * first slot, for an itemstack and if one if found attempt to add
         * it to the barrels this controller is connected to.
         */
        for (BlockFace face : connectableFaces) {
            Block conBlock = getBukkitBlock().getRelative(face);
            BlockState blockState = conBlock.getState();
            if (blockState instanceof InventoryHolder) {
                InventoryHolder holder = (InventoryHolder) blockState;
                Inventory inventory = holder.getInventory();
                int size = inventory.getSize();
                for (int slot = 0; slot < size; slot++) {
                    ItemStack item = inventory.getItem(slot);
                    if ((item == null) || (item.getType() == Material.AIR)) {
                        /* No item in this slot, keep trying */
                        continue;
                    }

                    int amount = item.getAmount();
                    addItems(inventory, slot, slot, 1);
                    /* Check for remaining items in the slot, if not stop checking compatible barrels */
                    item = inventory.getItem(slot);

                    if ((item == null) || (item.getType() == Material.AIR) || item.getAmount() != amount) {
                        /* The itemStack in the slot has be processed so stop further processing */
                        break;
                    }
                }
            }
        }
    }
}
