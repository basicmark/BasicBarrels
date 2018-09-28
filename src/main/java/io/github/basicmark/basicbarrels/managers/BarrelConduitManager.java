package io.github.basicmark.basicbarrels.managers;

import io.github.basicmark.basicbarrels.BasicBarrels;
import io.github.basicmark.basicbarrels.block.BarrelConduit;
import io.github.basicmark.extendminecraft.block.ExtendBlock;
import io.github.basicmark.extendminecraft.event.block.ExtendBlockChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class BarrelConduitManager implements Listener {
    BasicBarrels plugin;

    public BarrelConduitManager(BasicBarrels plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onConduitBlockUpdate(ExtendBlockChangeEvent event) {
        ExtendBlock fromExtBlock = event.getBlock();
        ExtendBlock toExtBlock = event.getAfterBlock();

        if (fromExtBlock instanceof BarrelConduit) {
            BarrelConduit conduit = (BarrelConduit) fromExtBlock;
            conduit.conduitRemove();
        }

        if (toExtBlock instanceof BarrelConduit) {
            BarrelConduit conduit = (BarrelConduit) toExtBlock;
            conduit.conduitScan();
        }
    }
}
