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
import org.bukkit.entity.Entity;
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
     * Version of data offsets we're using for itemstacks.
     *
     * Chances are we might need to extend the loreText pre-amble
     * so we version our data so we can support backwards compatibility.
     */
    static final int dataVersion = 1;

    /* Lore text offsets */
    static final int versionOffset = 0;

    /* Version 1 offsets */
    public static final int typeOffset = 1;

    /* Static class members */
    public static final String metadataKey = "BasicBarrel";
    public static final EnumSet<BarrelType> barrelSet = EnumSet.allOf(BarrelType.class);
    public static EnumSet<Material> logTypeSet = EnumSet.of(Material.ACACIA_LOG, Material.BIRCH_LOG, Material.DARK_OAK_LOG,
            Material.JUNGLE_LOG, Material.OAK_LOG, Material.SPRUCE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_BIRCH_LOG,
            Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG);
    private static final Set<Material> blacklist = new HashSet<Material>();
    private static final BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static ItemStack empty;

    /* Dynamic class members */
    private FixedMetadataValue metadata;
    private ItemFrame itemFrame;
    private BlockFace facing;

    /******************************************
     * State saved in the block configuration *
     * through ExtendMincraft                 *
     ******************************************/

    /* Save format version */
    private static final int saveDataVersion = 2;

    /* Save state in version 0 */
    private int amount;
    private BarrelType type;
    private UUID ownerUUID;
    private Material material;
    private byte blockData;
    UUID itemFrameUUID;

    /* Added to save state in version 1 */
    private String facingString;
    private ItemStack item;

    /* Added to save state in version 2 */
    private boolean locked;

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

        this.itemFrameUUID = itemFrame.getUniqueId();
        this.itemFrame.setItem(this.item);
        this.facing = hangingFace;
        this.facingString = this.facing.toString();
        BasicBarrels.logEvent(barrelLogPrefix() + ": Barrel placed by " + ownerUUID + ", type " + type + ", " + (locked ? "locked":"unlocked") + ", amount " + amount);
        metadata = new FixedMetadataValue(BarrelManager.plugin, this);
        setMetaData();
    }

    @Override
    public boolean load(ConfigurationSection config) {
        super.load(config);
        metadata = new FixedMetadataValue(BarrelManager.plugin, this);

        int loadVersion = config.getInt("version", 0);
        ownerUUID = UUID.fromString(config.getString("ownerUUID"));
        amount = config.getInt("amount");
        material = Material.valueOf(config.getString("material", Material.AIR.toString()));
        blockData = (byte) config.getInt("blockdata", 0);
        type = BarrelType.valueOf(config.getString("type"));

        /*
         * Earlier save formats didn't store the facing direction,
         * later versions should have itemstack and direction
         */
        if (loadVersion >= 1) {
            facingString = config.getString("facing", null);
            if (facingString != null) {
                facing = BlockFace.valueOf(facingString);
            }
            if (config.isItemStack("itemstack")) {
                item = config.getItemStack("itemstack");
            } else {
                item = null;
            }
        } else {
            item = null;
        }

        if (loadVersion >= 2) {
            locked = config.getBoolean("locked", true);
        } else {
            locked = true;
        }

        itemFrameUUID = UUID.fromString(config.getString("itemframe"));
        Collection<ItemFrame> entities = getBukkitBlock().getWorld().getEntitiesByClass​(ItemFrame.class);
        ItemFrame locaItemFrame = null;
        for (ItemFrame entity : entities) {
            if (entity.getUniqueId().equals(itemFrameUUID)) {
                locaItemFrame = entity;
                break;
            }
        }

        /*
         * There is a chance the itemframe is in a chunk which is not loaded in which case
         * we should not treat it as a loading error but will have to handle the fact that
         * the barrel is missing an itemframe but is registered with ExtendMinecraft and
         * so could have operations performed on it.
         */
        if (locaItemFrame == null) {
            /*
             * Build a set of chunk locations which might contain the itemframe ...
             */
            Set<Integer[]> chunkLocations = new HashSet<Integer[]>();
            if (facingString == null) {
                int chunkXOffset = getBukkitBlock().getX() & 0xf;
                int chunkZOffset = getBukkitBlock().getZ() & 0xf;
                if (((chunkXOffset == 0) || (chunkXOffset == 15)) || ((chunkZOffset == 0) || (chunkZOffset == 15))) {
                    chunkLocations.add(new Integer[]{(getBukkitBlock().getX() >> 4) - 1, (getBukkitBlock().getZ() >> 4) + 0});
                    chunkLocations.add(new Integer[]{(getBukkitBlock().getX() >> 4) + 1, (getBukkitBlock().getZ() >> 4) + 0});
                    chunkLocations.add(new Integer[]{(getBukkitBlock().getX() >> 4) + 0, (getBukkitBlock().getZ() >> 4) - 1});
                    chunkLocations.add(new Integer[]{(getBukkitBlock().getX() >> 4) + 0, (getBukkitBlock().getZ() >> 4) + 1});
                }
            } else {
                Integer[] chunkLoc = getItemFrameChunk();
                chunkLocations.add(chunkLoc);
            }

            /* .. then check to see which chunk(s) in the set are loaded... */
            Iterator<Integer[]> iter = chunkLocations.iterator();
            while (iter.hasNext()) {
                Integer[] chunkLocation = iter.next();
                if (getBukkitBlock().getWorld().isChunkLoaded(chunkLocation[0], chunkLocation[1])) {
                    iter.remove();
                }
            }

            /*
             * ... and if all the locations are loaded (and to get here we know we have no itemFrame)
             * then report failure to load the barrel. Otherwise ...
             */
            if (chunkLocations.isEmpty()) {
                BasicBarrels.logError("Failed to find itemframe for barrel @ " + getBukkitBlock().getLocation());
                return false;
            }

            /*
             * ... the itemFrame might be present but until the chunk its in is loaded we don't know
             * so we assume it will be present (by not reporting an error during loading) and register
             * with the manager to call back and check when the possible chunk(s) are loaded.
             */
            BasicBarrels.getBarrelManager().registerDeferredItemFrameLoad(this, chunkLocations);
            BasicBarrels.logEvent(barrelLogPrefix() + ": Barrel deferred load. Owner " + ownerUUID + ", type " + type + ", " + (locked ? "locked":"unlocked") + ", amount " + amount);
        }
        else {
            loadItemFrame(locaItemFrame);
        }

        return true;
    }

    private Integer[] getItemFrameChunk() {
        int x = getBukkitBlock().getX() >> 4;
        int z = getBukkitBlock().getZ() >> 4;
        switch (facing) {
            case NORTH:
                z -= 1;
                break;
            case SOUTH:
                z += 1;
                break;
            case EAST:
                x += 1;
                break;
            case WEST:
                x -= 1;
                break;
        }
        return new Integer[]{x,z};
    }

    private void loadItemFrame(ItemFrame barrelItemFrame) {
        /* If we didn't have a facing direction then work it out from the itemframe */
        if (facingString == null) {
            facing = barrelItemFrame.getFacing();
            facingString = facing.toString();
        }
        itemFrame = barrelItemFrame;

        /* If we didn't have an item then get the item from the frame */
        if (item == null) {
            item = itemFrame.getItem();
        }
        updateDisplay();
        BasicBarrels.logEvent(barrelLogPrefix() + ": Barrel loadItemFrame. Owner " + ownerUUID + ", type " + type + ", " + (locked ? "locked":"unlocked") + ", amount " + amount);
        setMetaData();
    }

    public boolean checkForItemFrame(Chunk chunk) {
        Entity entities[] = chunk.getEntities();
        for (Entity entity : entities) {
            if ((entity.getUniqueId().equals(itemFrameUUID)) && (entity instanceof ItemFrame)) {
                loadItemFrame((ItemFrame) entity);
                return true;
            }
        }
        return false;
    }

    @Override
    public void save(ConfigurationSection config) {
        super.save(config);
        config.set("version", saveDataVersion);
        config.set("ownerUUID", ownerUUID.toString());
        config.set("amount", amount);
        config.set("material", material.name());
        config.set("type", type.toString());
        config.set("blockdata", blockData);
        config.set("facing", facingString);
        config.set("itemframe", itemFrameUUID.toString());
        if (item != null) {
            config.set("itemstack", item);
        }
        config.set("locked", locked);
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
        if (itemFrame != null) {
            /*
             * We updating check if the itemframe object is still valid, it's possible the chunk with it
             * has been updated and reloaded since we found it or is now back in an unloaded chunk.
             */
            if (!itemFrame.isValid()) {
                /* Release our reference to the dead object */
                itemFrame = null;

                /* Get a list of chunk locations the itemframe could be in */
                Integer[] chunkLoc = getItemFrameChunk();
                if (getBukkitBlock().getWorld().isChunkLoaded(chunkLoc[0], chunkLoc[1])) {
                    checkForItemFrame(getBukkitBlock().getWorld().getChunkAt(chunkLoc[0], chunkLoc[1]));
                } else {
                    HashSet<Integer[]> chunkLocationSet = new HashSet<Integer[]>();
                    chunkLocationSet.add(chunkLoc);
                    BasicBarrels.getBarrelManager().registerDeferredItemFrameLoad(this, chunkLocationSet);
                }
            } else {
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
        }
    }

    public void unload() {
        BasicBarrels.logEvent(barrelLogPrefix() + ": Barrel removed");
        /* The unregister method handles if there was never a register call made. */
        BasicBarrels.getBarrelManager().unregisterDeferredItemFrameLoad(this);
        removeMetaData();
    }

    private void setMetaData() {
        if (itemFrame != null) {
            itemFrame.setMetadata(metadataKey, metadata);
            itemFrame.getLocation().getBlock().setMetadata(metadataKey, metadata);
        }
    }

    private void removeMetaData() {
        if (itemFrame != null) {
            itemFrame.removeMetadata(metadataKey, BarrelManager.plugin);
            itemFrame.getLocation().getBlock().removeMetadata(metadataKey, BarrelManager.plugin);
        }
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
        if (itemFrame != null) {
            itemFrame.setGlowing(highlight);
        }
    }

    public void breakBarrel(Player player) {
        boolean dropBarrelItem = true;
        if (player.getGameMode() == GameMode.CREATIVE) {
            dropBarrelItem = false;
        }
        breakBarrel(dropBarrelItem);
    }

    public boolean breakBarrel(boolean dropBarrelItem) {
        BasicBarrels.logEvent( barrelLogPrefix() +  ": Barrel broken dropping " + amount + " of " + item.getType());
        Location location = getBukkitBlock().getLocation();

        /*
         * Don't allow breaking of incomplete barrels as we might not know what they store and can't
         * remove their itemframe anyway.
         */
        if (itemFrame == null) {
            return false;
        }

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
        return true;
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

        /* Treat incomplete barrels as full as we don't know what they are storing */
        if (itemFrame == null) {
            return true;
        }

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

        /* Treat incomplete barrels as empty as we don't know what they are storing */
        if (itemFrame == null) {
            return;
        }

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
