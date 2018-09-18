package io.github.basicmark.basicbarrels.managers;

import io.github.basicmark.basicbarrels.BarrelType;
import io.github.basicmark.basicbarrels.BasicBarrels;
import io.github.basicmark.basicbarrels.block.Barrel;
import io.github.basicmark.basicbarrels.block.BarrelConduit;
import io.github.basicmark.basicbarrels.block.BarrelController;
import io.github.basicmark.basicbarrels.events.BasicBarrelBlockPlaceEvent;
import io.github.basicmark.extendminecraft.block.ExtendBlock;
import io.github.basicmark.extendminecraft.event.block.ExtendBlockChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class BarrelConduitManager implements Listener {
    BasicBarrels plugin;

    public BarrelConduitManager(BasicBarrels plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* TODO: Find a clearer way to do this */
    private boolean isConduit(ItemStack item) {
        if (BarrelType.itemStackIsBarrel(item, Barrel.typeOffset)) {
            return true;
        }
        if (BarrelController.itemStackIsBarrelController(item)) {
            return true;
        }
        return false;
    }

    @EventHandler
    public void onConduitBlockUpdate(ExtendBlockChangeEvent event) {
        ExtendBlock fromExtBlock = event.getBlock();
        ExtendBlock toExtBlock = event.getAfterBlock();

        if (fromExtBlock instanceof BarrelConduit) {
            BarrelConduit conduit = (BarrelConduit) fromExtBlock;
            conduit.conduitRemove();
            /*if (conduit.getController() != null) {
                conduit.getController().remove();
            }*/
        }

        if (toExtBlock instanceof BarrelConduit) {
            BarrelConduit conduit = (BarrelConduit) toExtBlock;
            conduit.conduitScan();
        }
    }
}
