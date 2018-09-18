package io.github.basicmark.basicbarrels.block;

import io.github.basicmark.basicbarrels.block.BarrelConduit;
import io.github.basicmark.extendminecraft.block.ExtendBlockFactory;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

public class BarrelConduitFactory extends ExtendBlockFactory<BarrelConduit>{
    public BarrelConduitFactory() {
        super("basicbarrels", "conduit");
    }

    @Override
    public BarrelConduit newBlock(Block block) {
        BarrelConduit extBlock =  new BarrelConduit(block, getFullName());

        return extBlock;
    }
}
