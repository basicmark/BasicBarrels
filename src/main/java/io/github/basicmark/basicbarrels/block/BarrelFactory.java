package io.github.basicmark.basicbarrels.block;

import io.github.basicmark.extendminecraft.block.ExtendBlockFactory;
import org.bukkit.block.Block;

public class BarrelFactory extends ExtendBlockFactory<Barrel> {
    public BarrelFactory() {
        super("basicbarrels", "barrel");
    }

    @Override
    public Barrel newBlock(Block block) {
        Barrel extBlock =  new Barrel(block, getFullName());

        return extBlock;
    }
}
