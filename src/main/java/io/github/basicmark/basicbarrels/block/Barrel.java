package io.github.basicmark.basicbarrels.block;

import io.github.basicmark.basicbarrels.BarrelException;
import io.github.basicmark.basicbarrels.BasicBarrels;

import io.github.basicmark.basicbarrels.managers.BarrelManager;
import io.github.basicmark.basicbarrels.BarrelType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionData;
import org.bukkit.util.Consumer;

import java.util.*;

public class Barrel extends BarrelConduit {
    /*
     * Version of data offsets we're using.
     *
     * Chances are we might need to extend the loreText pre-amble
     * so we version our data so we can support backwards compatibility.
     */
    static final int dataVersion = 1;

    /* Lore text offsets */
    static final int versionOffset = 0;

    /* Version 1 offsets */
    public static final int typeOffset = 1;

    public static final String metadataKey = "BasicBarrel";
    public static final EnumSet<BarrelType> barrelSet = EnumSet.allOf(BarrelType.class);
    public static EnumSet<Material> logTypeSet = EnumSet.of(Material.ACACIA_LOG, Material.BIRCH_LOG, Material.DARK_OAK_LOG,
            Material.JUNGLE_LOG, Material.OAK_LOG, Material.SPRUCE_LOG);
    private static final Set<Material> blacklist = new HashSet<Material>();
    private static final BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static ItemStack empty;

    private int amount;
    private ItemStack item;
    private BarrelType type;
    private UUID ownerUUID;
    private boolean locked;
    private Material material;
    private byte blockData;
    private ItemFrame itemFrame;


    static {
        empty = new ItemStack(Material.BARRIER);
        ItemMeta meta = empty.getItemMeta();
        meta.setDisplayName("Empty");
        empty.setItemMeta(meta);
    }

    public Barrel(Block block, String fullName) {
        super(block, fullName);
    }

    public void init(Player player, ItemStack barrelItem, boolean locked) {
        this.ownerUUID = player.getUniqueId();
        this.locked = locked;
        this.amount = 0;
        this.item = empty.clone();
        this.type = BarrelType.fromName(barrelItem.getItemMeta().getLore().get(1));
        this.material = barrelItem.getType();
        this.blockData = barrelItem.getData().getData();

        /* Create the barrel */
        Block block = getBukkitBlock();
        World world = getBukkitBlock().getWorld();

        final BlockFace hangingFace = faces[(Math.round(player.getLocation().getYaw() / 90.0F) & 0x3)];
        Location inFrontLoc = block.getRelative(hangingFace).getLocation();
        this.itemFrame = world.spawn(inFrontLoc, ItemFrame.class, new Consumer<ItemFrame>() {
            public void accept(ItemFrame itemFrame) {
                itemFrame.setFacingDirection(hangingFace);
            }
        });

        this.itemFrame.setItem(this.item);
        BasicBarrels.logEvent(barrelLogPrefix() + ": Barrel placed by " + ownerUUID + " of type " + type + " and is " + (locked ? "locked":"unlocked"));
        setMetaData();
    }

    @Override
    public boolean load(ConfigurationSection config) {
        super.load(config);
        ownerUUID = UUID.fromString(config.getString("ownerUUID"));
        amount = config.getInt("amount");
        material = Material.valueOf(config.getString("material", Material.AIR.toString()));
        blockData = (byte) config.getInt("blockdata", 0);
        type = BarrelType.valueOf(config.getString("type"));
        UUID itemFrameUUID = UUID.fromString(config.getString("itemframe"));
        Collection<ItemFrame> entities = getBukkitBlock().getWorld().getEntitiesByClass​(ItemFrame.class);
        itemFrame = null;
        for (ItemFrame entity : entities) {
            if (entity.getUniqueId().equals(itemFrameUUID)) {
                itemFrame = entity;
                break;
            }
        }
        if (itemFrame == null) {
            BasicBarrels.logError("Failed to find itemframe for barrel @ " + getBukkitBlock().getLocation());
            return false;
        }
        item = itemFrame.getItem();
        updateDisplay();
        BasicBarrels.logEvent(barrelLogPrefix() + ": Barrel loaded. Owner " + ownerUUID + ", type " + type + ", locked " + (locked ? "locked":"unlocked"));
        setMetaData();
        return true;
    }

    @Override
    public void save(ConfigurationSection config) {
        super.save(config);
        config.set("ownerUUID", ownerUUID.toString());
        config.set("amount", amount);
        config.set("material", material.name());
        config.set("type", type.toString());
        config.set("blockdata", blockData);
        config.set("itemframe", itemFrame.getUniqueId().toString());
    }

    public ItemStack changeBarrel(ItemStack barrelItem) {
        ItemStack oldBarrel = createItemStack(type, material);
        BarrelType oldType = type;
        Orientable orientable = (Orientable) getBukkitBlock().getBlockData();
        Axis axis = orientable.getAxis();

        type = BarrelType.fromName(barrelItem.getItemMeta().getLore().get(1));
        material = barrelItem.getType();

        getBukkitBlock().setType(material);
        BlockState state;
        state = getBukkitBlock().getState();
        orientable = (Orientable) state.getBlockData();
        orientable.setAxis(axis);
        state.setBlockData(orientable);
        state.update();

        BasicBarrels.logEvent(barrelLogPrefix() + ": Barrel changed from " + oldType + " to " + type);
        return oldBarrel;
    }

    public static void blacklistAdd(Material material) {
        blacklist.add(material);
    }

    public static void blacklistReset() {
        blacklist.clear();
    }

    public static ItemStack createItemStack(BarrelType type, Material material) {
        /* Create the ItemStack we wish to produce */
        ItemStack item = new ItemStack(material, 1);
        ItemMeta data = item.getItemMeta();
        data.setDisplayName(type.getName());
        List<String> loreText = new ArrayList<String>();

        loreText.add(((Integer)dataVersion).toString());
        loreText.add(type.getName());
        data.setLore(loreText);
        item.setItemMeta(data);
        return item;
    }

    private void updateDisplay() {
        if (amount == 0) {
            itemFrame.setItem(empty.clone());
        } else {
            Integer stackSize = item.getMaxStackSize();
            Integer remain = amount % stackSize;
            int stackCount = (amount - remain) / stackSize;
            ItemMeta barrelMeta = item.getItemMeta();
            barrelMeta.setDisplayName(stackCount + "*" + stackSize + "+" + remain);
            item.setItemMeta(barrelMeta);
            itemFrame.setItem(item);
        }
    }

    public void removeBarrel() {
        BasicBarrels.logEvent(barrelLogPrefix() + ": Barrel removed");
        removeMetaData();
    }

    private void setMetaData() {
        Block block = getBukkitBlock();
        FixedMetadataValue metadata = new FixedMetadataValue(BarrelManager.plugin, this);
        itemFrame.setMetadata(metadataKey, metadata);
        itemFrame.getLocation().getBlock().setMetadata(metadataKey, metadata);
    }

    private void removeMetaData() {
        Block block = getBukkitBlock();
        itemFrame.removeMetadata(metadataKey, BarrelManager.plugin);
        itemFrame.getLocation().getBlock().removeMetadata(metadataKey, BarrelManager.plugin);
    }

    public boolean hasPermission(Player player) {
        /* Lock bypass all bypass all the checks below */
        if (player.hasPermission("barrel.mod.lockbypass")) {
            return true;
        }

        if (!player.hasPermission("barrel.player.use")) {
            return false;
        }

        if (!locked) {
            return true;
        }

        return player.getUniqueId().equals(ownerUUID);
    }

    public BarrelType getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getOwner() {
        return ownerUUID.toString();
    }

    public ItemStack getItem() {
        return item;
    }

    public void setHighlight(boolean highlight) {
        itemFrame.setGlowing(highlight);
    }

    public void breakBarrel(Player player) {
        boolean dropBarrelItem = true;
        if (player.getGameMode() == GameMode.CREATIVE) {
            dropBarrelItem = false;
        }
        breakBarrel(dropBarrelItem);
    }

    public void breakBarrel(boolean dropBarrelItem) {
        BasicBarrels.logEvent( barrelLogPrefix() +  ": Barrel broken dropping " + amount + " of " + item.getType());
        Location location = getBukkitBlock().getLocation();

        /* Spawn items from where the barrel being broken is */
        while (amount != 0) {
            int dropCount = Math.min(amount, item.getMaxStackSize());

            location.getWorld().dropItemNaturally(location, itemStackFromBarrel(dropCount));
            amount -= dropCount;
        }

        /* Drop an item stack which represents the barrel being broken */
        if (dropBarrelItem) {
            location.getWorld().dropItemNaturally(location, createItemStack(type, material));
        }

        /* Remove items which represent the barrel in the world */
        itemFrame.remove();
        BlockState state = location.getBlock().getState();
        state.setType(Material.AIR);
        state.update(true);

        BasicBarrels.getExtendMinecraft().clearBlock(this);
        removeMetaData();
    }

    public boolean isEmpty() {
        return amount == 0;
    }

    public int getMaxStackSize() {
        return item.getMaxStackSize();
    }

    public void lock(UUID uuid) {
        locked = true;
        ownerUUID = uuid;
    }

    public void unlock() {
        locked = false;
    }

    public boolean addItemsFromInventory(Inventory inventory, int firstSlot, int lastSlot, int addAmount) throws BarrelException {
        int beforeAmount = this.amount;

        /*
         * If the barrel is empty set what it is to store based on the first slot, other
         * slots might contain other items in which case they will not be stored, but this
         * is true for barrels whose content has already been set.
         */
        if (amount == 0) {
            if (!isStoreable(inventory.getItem(firstSlot))) {
                return true;
            }

            item = inventory.getItem(firstSlot).clone();
            item.setAmount(1);
            itemFrame.setItem(item);
        }

        /* Loop through the permitted slots and transfer the items that would fit */
        int space = (type.getSize() * item.getMaxStackSize()) - amount;
        int toTransfer = Math.min(space, addAmount);
        for (int i = firstSlot; i <= lastSlot; i++) {
            ItemStack addItem = inventory.getItem(i);
            if (matchsBarrelItem(addItem)) {
                int stackSize = Math.min(toTransfer, addItem.getAmount());
                if (stackSize >= addItem.getAmount()) {
                    inventory.clear(i);
                } else {
                    addItem.setAmount(addItem.getAmount() - stackSize);
                }
                amount += stackSize;
                toTransfer -= stackSize;
                if (toTransfer == 0) {
                    break;
                }
            }
        }
        if (beforeAmount != amount) {
            BasicBarrels.logEvent(barrelLogPrefix() + " : Added items to barrel (from " + beforeAmount + " to " + amount + ")");
            updateDisplay();
        }
        if ((type.getSize() * item.getMaxStackSize()) == amount) {
            return true;
        }

        return false;
    }

    public void removeItemsToInventory(Inventory inventory, int firstSlot, int lastSlot, int removeAmount) {
        int beforeAmount = this.amount;
        /*
         * If the barrel is empty there is nothing to remove!
         */
        if (amount == 0) {
            return;
        }

        /* Loop through the permitted slots and transfer the items that would fit */
        int toTransfer = Math.min(amount, removeAmount);
        for (int i = firstSlot; i <= lastSlot; i++) {
            ItemStack removeItem = inventory.getItem(i);
            int stackSize = 0;
            if ((removeItem == null) || (removeItem.getType() == Material.AIR)) {
                /* The slot is empty spawn a new item stack and add it to the inventory */
                stackSize = Math.min(toTransfer, item.getMaxStackSize());
                inventory.setItem(i, itemStackFromBarrel(stackSize));
            } else if (matchsBarrelItem(removeItem)) {
                /* The slot is not empty so fill it up until the barrel is empty */
                stackSize = Math.min(toTransfer, removeItem.getMaxStackSize() - removeItem.getAmount());
                removeItem.setAmount(removeItem.getAmount() + stackSize);
            } else {
                stackSize = 0;
            }

            amount -= stackSize;
            toTransfer -= stackSize;

            if (toTransfer == 0) {
                break;
            }
        }

        if (beforeAmount != amount) {
            BasicBarrels.logEvent(barrelLogPrefix() + " : Removed items to barrel (from " + beforeAmount + " to " + amount + ")");
            updateDisplay();
        }
    }

    static public boolean isBarrelLocation(Location location) {
        return location.getBlock().hasMetadata(metadataKey);
    }

    static public boolean isBarrelBlock(Block block) {
        return block.hasMetadata(metadataKey);
    }

    protected String barrelLogPrefix() {
        Location location = getBukkitBlock().getLocation();
        return location.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
    }

    static public boolean isStoreable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName() || meta.hasEnchants() || meta.hasLore())
                return false;
            if (meta instanceof Damageable) {
                Damageable damageable = (Damageable) meta;
                if (damageable.hasDamage()) {
                    return false;
                }
            }
        }
        if (BarrelManager.shulkerBoxes.contains(item.getType())) {
            return false;
        }
        if (blacklist.contains(item.getType()))
            return false;

        return true;
    }

    public boolean matchsBarrelItem(ItemStack addItem) {
        if (addItem == null) {
            return false;
        }

        /* Items which contain data which can't be stored will not match */
        if (!isStoreable(addItem)) {
            return false;
        }

        if (addItem.getType() != item.getType()) {
            return false;
        }

        if (item.getType().equals(Material.POTION) || item.getType().equals(Material.SPLASH_POTION) ||
                item.getType().equals(Material.LINGERING_POTION) ||
                item.getType().equals(Material.TIPPED_ARROW)) {
            PotionMeta barrelMeta = (PotionMeta) item.getItemMeta();
            PotionMeta addMeta = (PotionMeta) addItem.getItemMeta();
            PotionData barrelData = barrelMeta.getBasePotionData();
            PotionData addData = addMeta.getBasePotionData();

            return barrelData.equals(addData);
        }
        return true;
    }

    private ItemStack itemStackFromBarrel(int stackSize) {
        ItemStack newStack;

        if (item.getType().equals(Material.POTION) ||
                item.getType().equals(Material.SPLASH_POTION) ||
                item.getType().equals(Material.LINGERING_POTION) ||
                item.getType().equals(Material.TIPPED_ARROW)) {
            newStack = new ItemStack(item.getType(), stackSize);
            PotionMeta pMeta = (PotionMeta) item.getItemMeta();
            pMeta.setDisplayName(null);
            pMeta.setLore(null);
            newStack.setItemMeta(pMeta);
        } else {
            newStack = new ItemStack(item.getType(), stackSize);
            newStack.setData(item.getData());
        }
        return newStack;
    }
}
