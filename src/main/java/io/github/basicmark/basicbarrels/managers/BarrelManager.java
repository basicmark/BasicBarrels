package io.github.basicmark.basicbarrels.managers;

import io.github.basicmark.basicbarrels.BarrelException;
import io.github.basicmark.basicbarrels.BarrelType;
import io.github.basicmark.basicbarrels.BasicBarrels;
import io.github.basicmark.basicbarrels.block.Barrel;
import io.github.basicmark.basicbarrels.events.BasicBarrelBlockBreakEvent;
import io.github.basicmark.basicbarrels.events.BasicBarrelBlockPlaceEvent;
import io.github.basicmark.extendminecraft.block.ExtendBlock;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.metadata.MetadataValue;

import java.util.*;

public class BarrelManager implements Listener {
    /* TODO move to common class */
    public static EnumSet<Material> shulkerBoxes = EnumSet.of(Material.BLACK_SHULKER_BOX, Material.SHULKER_BOX,
            Material.BROWN_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,Material.GREEN_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX, Material.LIME_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.PINK_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.WHITE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX, Material.BLUE_SHULKER_BOX);
    private static final BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    public static BasicBarrels plugin = null;
    private static Boolean defaultLocking = true;
    private Set<Recipe> recipes = new HashSet<Recipe>();
    private Map<Player, BarrelOperation> playerOperation = new HashMap<Player, BarrelOperation>();;

    public BarrelManager(BasicBarrels barrelPlugin) {
        plugin = barrelPlugin;

        /* Register the different types of barrels that can be crafted */
        for (BarrelType barrel : Barrel.barrelSet) {
            Integer dataTypeIndex = 0;
            for (Material log : Barrel.logTypeSet) {
                ItemStack item = Barrel.createItemStack(barrel, log);
                ShapedRecipe recipe = new ShapedRecipe(item);

                recipe.shape("EME","MLM","EME");
                recipe.setIngredient('L', log);
                recipe.setIngredient('M', barrel.getMaterial());
                recipe.setIngredient('E', Material.ENDER_PEARL);

                Bukkit.getServer().addRecipe(recipe);
                recipes.add(recipe);
            }
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private Barrel getBarrel(Block block) {
        ExtendBlock extBlock = BasicBarrels.getExtendMinecraft().getBlock(block);
        if (extBlock == null) {
            return null;
        }
        if (!(extBlock instanceof Barrel)) {
            return null;
        }

        return (Barrel) extBlock;
    }

    private void setBarrel(Barrel barrel) {
        BasicBarrels.getExtendMinecraft().setBlock(barrel);
    }

    public void setPendingRequest(Player player, BarrelOperation operation) {
        playerOperation.put(player, operation);
    }

    public static void setDefaultLocking(boolean value) {
        defaultLocking = value;
    }

    private boolean handlePendingRequest(Barrel barrel, Player player) {
        /* Check if the player has a pending operation */
        if (playerOperation.containsKey(player)) {

            switch (playerOperation.get(player)) {
                case LOCK:
                    barrel.lock(player.getUniqueId());
                    player.sendMessage(plugin.translateMessage("BARREL_LOCK"));
                    break;
                case UNLOCK:
                    barrel.unlock();
                    player.sendMessage(plugin.translateMessage("BARREL_UNLOCK"));
                    break;
                case INFO :
                    double stacksUsed = Math.ceil((double) barrel.getAmount() / (double) barrel.getMaxStackSize());
                    Map<String,String> substitutes = new HashMap<String, String>();
                    substitutes.put("%TYPE%", barrel.getType().toString());
                    substitutes.put("%STACKSUSED%", Integer.toString((int) stacksUsed));
                    substitutes.put("%STACKSMAX%", Integer.toString(barrel.getType().getSize()));
                    substitutes.put("%OWNER%", barrel.getOwner());


                    player.sendMessage(plugin.translateMessage("BARREL_INFO1", substitutes));
                    player.sendMessage(plugin.translateMessage("BARREL_INFO2", substitutes));
                    player.sendMessage(plugin.translateMessage("BARREL_INFO3", substitutes));
                    if (barrel.isLocked()) {
                        player.sendMessage(plugin.translateMessage("BARREL_INFO4", substitutes));
                    } else {
                        player.sendMessage(plugin.translateMessage("BARREL_INFO5", substitutes));
                    }
            }
            playerOperation.remove(player);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPrepareItemCraftEvent(PrepareItemCraftEvent event) {
        /* Check if the player is allowed to craft a managers */
        if ((recipes.contains(event.getRecipe()) && (!event.getView().getPlayer().hasPermission("managers.player.craft")))) {
            event.getInventory().setResult(null);
        }

        /* Stop players from crafting with barrels */
        ItemStack items[] = event.getInventory().getMatrix();
        for (ItemStack item : items) {
            if ((item != null) && (Barrel.logTypeSet.contains(item.getType()))) {
                if (BarrelType.itemStackIsBarrel(item, Barrel.typeOffset)) {
                    event.getInventory().setResult(null);
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlaceEvent(BlockPlaceEvent event) {
        try {
            /* Don't process our dummy events */
            if (event instanceof BasicBarrelBlockPlaceEvent) {
                return;
            }

            /* Check the block isn't going to obstruct the item frame */
            Player player = event.getPlayer();
            if (Barrel.isBarrelLocation(event.getBlock().getLocation())) {
                throw new BarrelException(BarrelException.Reason.AIR_GAP);
            }

            /* Ignore non-managers placements */
            ItemStack barrelItem = event.getItemInHand();
            if (!BarrelType.itemStackIsBarrel(barrelItem, Barrel.typeOffset)) {
                return;
            }

            /* Check permissions */
            if (!player.hasPermission("managers.player.place")) {
                throw new BarrelException(BarrelException.Reason.BARREL_PERMISSION);
            }

            /* Check there is space in front of the Barrel */
            Block block = event.getBlockPlaced();
            BlockFace face = faces[Math.round(player.getLocation().getYaw() / 90f) & 3];
            Block inFrontBlock = block.getRelative(face);

            if (inFrontBlock.getType() != Material.AIR) {
                throw new BarrelException(BarrelException.Reason.AIR_GAP);
            }

            /* Create a dummy event to check that another plug-in wouldn't cancel it */
            BasicBarrelBlockPlaceEvent customEvent = new BasicBarrelBlockPlaceEvent(event.getBlock(),
                    event.getBlockReplacedState(), event.getBlockAgainst(),
                    event.getItemInHand(), player, true);
            Bukkit.getServer().getPluginManager().callEvent(customEvent);
            if (customEvent.isCancelled()) {
                throw new BarrelException(BarrelException.Reason.BLOCK_PERMISSION);
            }

            /* Create the managers */
            Barrel barrel = (Barrel) BasicBarrels.getExtendMinecraft().blockRegistry.getLoader("basicbarrels:barrel").newBlock(block);
            barrel.init(player, barrelItem, defaultLocking);
            setBarrel(barrel);
        } catch (BarrelException e) {
            event.getPlayer().sendMessage(plugin.translateMessage(e.getReason().toString()));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        try {
            /* Don't process our dummy events */
            if (event instanceof BasicBarrelBlockBreakEvent) {
                return;
            }
            Block block = event.getBlock();
            Barrel barrel = getBarrel(block);
            if (barrel == null) {
                return;
            }

            Player player = event.getPlayer();

            /* Cancel regardless of if the player has perms or not */
            event.setCancelled(true);
            /* Check permissions */
            if (!player.hasPermission("managers.player.break")) {
                throw new BarrelException(BarrelException.Reason.BARREL_PERMISSION);
            }

            if (!barrel.hasPermission(player)) {
                throw new BarrelException(BarrelException.Reason.BARREL_PERMISSION);
            }

            /* Create a dummy event to check that another plug-in wouldn't cancel it */
            BasicBarrelBlockBreakEvent customEvent = new BasicBarrelBlockBreakEvent(block, player);
            Bukkit.getServer().getPluginManager().callEvent(customEvent);
            if (customEvent.isCancelled()) {
                throw new BarrelException(BarrelException.Reason.BLOCK_PERMISSION);
            }

            barrel.breakBarrel(player);
        } catch (BarrelException e) {
            event.getPlayer().sendMessage(plugin.translateMessage(e.getReason().toString()));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent event) {
        try {
            Entity entity = event.getRightClicked();
            Player player = event.getPlayer();
            ItemStack handItem = player.getInventory().getItemInMainHand();

            List<MetadataValue> metadata = entity.getMetadata(Barrel.metadataKey);
            if (metadata.isEmpty()) {
                return;
            }

            Barrel barrel = (Barrel) metadata.get(0).value();

            /* What ever happens we want to stop the event */
            event.setCancelled(true);

            /* Check if the interaction is valid and if not return */
            if (handItem == null) {
                return;
            }

            if (handItem.getType() == Material.AIR) {
                return;
            }

            /* Check player has permission and if not return */
            if (!barrel.hasPermission(player)) {
                throw new BarrelException(BarrelException.Reason.BARREL_PERMISSION);
            }

            /* Check if the player has a barrel item and switch it out if there isn't a space problem */
            if (BarrelType.itemStackIsBarrel(handItem, Barrel.typeOffset)) {
                BarrelType to = BarrelType.itemStackBarrelType(handItem, Barrel.typeOffset);
                int stacks = Math.round(barrel.getAmount() / barrel.getMaxStackSize());
                if (stacks > to.getSize()) {
                    throw new BarrelException(BarrelException.Reason.SWITCH_SPACE);
                }

                ItemStack oldBarrel = barrel.changeBarrel(handItem);
                if (handItem.getAmount() > 1) {
                    handItem.setAmount(handItem.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
                Map<Integer, ItemStack> remainItems = player.getInventory().addItem(oldBarrel);
                for (ItemStack remainItem : remainItems.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), remainItem);
                }
                /* Although the operation was a success throw the exception to avoid the normal processing */
                throw new BarrelException(BarrelException.Reason.SWITCH_SUCCESS);
            }

            /* Remove items from the player and add them to the barrel */
            if (shulkerBoxes.contains(player.getInventory().getItemInMainHand().getType())) {
                ItemStack item = player.getInventory().getItemInMainHand();
                BlockStateMeta data = (BlockStateMeta) item.getItemMeta();
                ShulkerBox shulkerBox = (ShulkerBox) data.getBlockState();
                Inventory inventory = shulkerBox.getInventory();

                if (player.isSneaking()) {
                    barrel.addItemsFromInventory(inventory, 0, inventory.getSize() - 1, Integer.MAX_VALUE);
                } else {
                    barrel.addItemsFromInventory(inventory, 0, inventory.getSize() - 1, barrel.getMaxStackSize());
                }
                data.setBlockState(shulkerBox);
                item.setItemMeta(data);
            } else {
                PlayerInventory inventory = player.getInventory();

                if (player.isSneaking()) {
                    /* If the barel is empty then add the held item first to set the contents */
                    if (barrel.isEmpty()) {
                        barrel.addItemsFromInventory(inventory, inventory.getHeldItemSlot(), inventory.getHeldItemSlot(), barrel.getMaxStackSize());
                    }
                    barrel.addItemsFromInventory(inventory, 0, inventory.getStorageContents().length - 1, Integer.MAX_VALUE);
                } else {
                    barrel.addItemsFromInventory(inventory, inventory.getHeldItemSlot(), inventory.getHeldItemSlot(), barrel.getMaxStackSize());
                }
            }
        } catch (BarrelException e) {
            event.getPlayer().sendMessage(plugin.translateMessage(e.getReason().toString()));
        }
    }

    @EventHandler
    public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
        try {
            Entity damager = event.getDamager();
            Entity entity = event.getEntity();

            List<MetadataValue> metadata = entity.getMetadata(Barrel.metadataKey);
            if (metadata.isEmpty()) {
                return;
            }

            Barrel barrel = (Barrel) metadata.get(0).value();

            /* What ever happens we want to stop the event */
            event.setCancelled(true);

            if (!(damager instanceof Player)) {
                return;
            }

            Player player = (Player) damager;
            if (handlePendingRequest(barrel, player)) {
                return;
            }

            /* Check player has permission and if not return */
            if (!barrel.hasPermission(player)) {
                throw new BarrelException(BarrelException.Reason.BARREL_PERMISSION);
            }

            /* Remove items from the managers and add them to the player */
            if (shulkerBoxes.contains(player.getInventory().getItemInMainHand().getType())) {
                ItemStack item = player.getInventory().getItemInMainHand();
                BlockStateMeta data = (BlockStateMeta) item.getItemMeta();
                ShulkerBox shulkerBox = (ShulkerBox) data.getBlockState();
                Inventory inventory = shulkerBox.getInventory();

                if (player.isSneaking()) {
                    barrel.removeItemsToInventory(inventory, 0, inventory.getSize(), barrel.getMaxStackSize());
                } else {
                    barrel.removeItemsToInventory(inventory, 0, inventory.getSize(), 1);
                }
                data.setBlockState(shulkerBox);
                item.setItemMeta(data);
            } else {
                PlayerInventory inventory = player.getInventory();
                if (player.isSneaking()) {
                    barrel.removeItemsToInventory(inventory, 0, inventory.getStorageContents().length - 1, barrel.getMaxStackSize());
                } else {
                    barrel.removeItemsToInventory(inventory, 0, inventory.getStorageContents().length - 1, 1);
                }
            }
        } catch (BarrelException e) {
            event.getDamager().sendMessage(plugin.translateMessage(e.getReason().toString()));
        }
    }

    @EventHandler
    public void onEntityDamageEvent(EntityDamageEvent event) {
        List<MetadataValue> metadata = event.getEntity().getMetadata(Barrel.metadataKey);

        /* Stop other forms of damage from effecting managers itemframes */
        if (!metadata.isEmpty()) {
            if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHangingBreakEvent(HangingBreakEvent event) {
        List<MetadataValue> metadata = event.getEntity().getMetadata(Barrel.metadataKey);
        if (!metadata.isEmpty()) {
            Barrel barrel = (Barrel) metadata.get(0).value();
            Block block = barrel.getBukkitBlock();

            /* Only drop the managers when the itemframe has nothing to hang from. */
            if (Barrel.logTypeSet.contains(block.getType())) {
                event.setCancelled(true);
            } else {
                barrel.breakBarrel(true);
            }
        }
    }

    public enum BarrelOperation {
        LOCK, UNLOCK, INFO
    }
}
