package io.github.basicmark.basicbarrels.block;

import io.github.basicmark.extendminecraft.block.ExtendBlockFactory;
import org.bukkit.block.Block;

public class BarrelControllerFactory extends ExtendBlockFactory<BarrelController>  {
    public BarrelControllerFactory() {
        super("basicbarrels", "barrelcontroller");
    }

    @Override
    public BarrelController newBlock(Block block) {
        BarrelController extBlock = new BarrelController(block, getFullName());
        return extBlock;
    }
}
