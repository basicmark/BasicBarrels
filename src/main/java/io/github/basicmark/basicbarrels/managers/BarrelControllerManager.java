package io.github.basicmark.basicbarrels.managers;

import io.github.basicmark.basicbarrels.BarrelException;
import io.github.basicmark.basicbarrels.BasicBarrels;
import io.github.basicmark.basicbarrels.block.BarrelController;
import io.github.basicmark.basicbarrels.events.BasicBarrelBlockPlaceEvent;
import io.github.basicmark.extendminecraft.ExtendMinecraft;
import io.github.basicmark.extendminecraft.block.ExtendBlock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BarrelControllerManager implements Listener {
    static public BasicBarrels plugin;
    private ShapedRecipe recipe;
    private ItemStack controllerItem;
    Set<BarrelController> controllers = new HashSet<BarrelController>();
    BukkitTask tickerTask = null;
    private Map<Player, ControllerOperation> playerOperation = new HashMap<Player, ControllerOperation>();;

    public BarrelControllerManager(BasicBarrels barrelPlugin) {
        this.plugin = barrelPlugin;
        controllerItem = new ItemStack(Material.OBSERVER,1);
        ItemMeta meta = controllerItem.getItemMeta();
        List<String> lore = new ArrayList<String>();
        lore.add(Integer.toString(BarrelController.dataVersion));
        lore.add(BarrelController.getName());
        meta.setLore(lore);
        controllerItem.setItemMeta(meta);

        recipe = new ShapedRecipe(controllerItem);

        recipe.shape("RQR","COC","RQR");
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        recipe.setIngredient('Q', Material.QUARTZ);
        recipe.setIngredient('C', Material.COMPARATOR);
        recipe.setIngredient('O', Material.OBSERVER);

        Bukkit.getServer().addRecipe(recipe);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        stopTicker();
    }

    @EventHandler
    public void onBlockPlaceEvent(BlockPlaceEvent event) {
        try {
            /* Don't process our dummy events */
            if (event instanceof BasicBarrelBlockPlaceEvent) {
                return;
            }

            /* Ignore non-managers controller placements */
            ItemStack controllerItem = event.getItemInHand();
            if (!BarrelController.itemStackIsBarrelController(controllerItem)) {
                return;
            }

            /* Check permissions */
            Player player = event.getPlayer();
            if (!player.hasPermission("managers.player.place")) {
                throw new BarrelException(BarrelException.Reason.BARREL_PERMISSION);
            }

            /* Create a dummy event to check that another plug-in wouldn't cancel it */
            BasicBarrelBlockPlaceEvent customEvent = new BasicBarrelBlockPlaceEvent(event.getBlock(),
                    event.getBlockReplacedState(), event.getBlockAgainst(),
                    event.getItemInHand(), player, true);
            Bukkit.getServer().getPluginManager().callEvent(customEvent);
            if (customEvent.isCancelled()) {
                throw new BarrelException(BarrelException.Reason.BLOCK_PERMISSION);
            }

            /* Create the managers controller */
            Block block = event.getBlock();
            BarrelController controller = (BarrelController) BasicBarrels.getExtendMinecraft().blockRegistry.getLoader("basicbarrels:barrelcontroller").newBlock(block);
            ExtendMinecraft extMinecraft = BasicBarrels.getExtendMinecraft();
            extMinecraft.setBlock(controller);
        } catch (BarrelException e) {
            event.getPlayer().sendMessage(plugin.translateMessage(e.getReason().toString()));
            event.setCancelled(true);
        }
    }

    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BarrelController controller : controllers) {
                    controller.tick();
                }
            }
        }.runTaskTimer(this.plugin, 4,4);
    }

    private void stopTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
        }
    }
    public void setPendingRequest(Player player, ControllerOperation operation) {
        playerOperation.put(player, operation);
    }

    public void registerController(BarrelController controller) {
        if (controllers.isEmpty()) {
            startTicker();
        }
        controllers.add(controller);
    }

    public void unregisterController(BarrelController controller) {
        controllers.remove(controller);

        if (controllers.isEmpty()) {
            stopTicker();
        }
    }

    private boolean handlePendingRequest(BarrelController controller, Player player) {
        /* Check if the player has a pending operation */
        if (playerOperation.containsKey(player)) {
            switch (playerOperation.get(player)) {
                case CONNECTED:
                    controller.showConnectedBarrels();
                    player.sendMessage(plugin.translateMessage("CONTROLLER_BARRELS_HIGHLIGHTED"));
                    break;
            }
            playerOperation.remove(player);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        ExtendMinecraft extMinecraft = BasicBarrels.getExtendMinecraft();
        ExtendBlock extBlock = extMinecraft.getBlock(event.getBlock());

        if (!(extBlock instanceof BarrelController)) {
            return;
        }

        BarrelController controller = (BarrelController) extBlock;
        controller.removeBarrelController();
        extMinecraft.clearBlock(extBlock);

        Player player = event.getPlayer();
        player.getWorld().dropItemNaturally(event.getBlock().getLocation(), controllerItem);
    }

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        ExtendMinecraft extMinecraft = BasicBarrels.getExtendMinecraft();

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ExtendBlock extBlock = extMinecraft.getBlock(event.getClickedBlock());
        if (!(extBlock instanceof BarrelController)) {
            return;
        }

        BarrelController controller = (BarrelController) extBlock;
        Player player = event.getPlayer();

        event.setCancelled(true);
        if (handlePendingRequest(controller, player)) {
            return;
        }

        /* Remove items from the player and add them to the barrel */
        if (BarrelManager.shulkerBoxes.contains(player.getInventory().getItemInMainHand().getType())) {
            ItemStack item = player.getInventory().getItemInMainHand();
            BlockStateMeta data = (BlockStateMeta) item.getItemMeta();
            ShulkerBox shulkerBox = (ShulkerBox) data.getBlockState();
            Inventory inventory = shulkerBox.getInventory();

            controller.addItems(inventory, 0, inventory.getSize() - 1, Integer.MAX_VALUE);
            data.setBlockState(shulkerBox);
            item.setItemMeta(data);
        } else {
            PlayerInventory inventory = player.getInventory();

            if (player.isSneaking()) {
                /* TODO: Fix shift clicking when there is no barrel for an item? */
                controller.addItems(inventory, 0, inventory.getStorageContents().length - 1, Integer.MAX_VALUE);
            } else {
                controller.addItems(inventory, inventory.getHeldItemSlot(), inventory.getHeldItemSlot(), Integer.MAX_VALUE);
            }
        }
    }

    public enum ControllerOperation {
        CONNECTED
    }
}
