package io.github.basicmark.basicbarrels.events;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

public class BasicBarrelBlockBreakEvent extends BlockBreakEvent {
    public BasicBarrelBlockBreakEvent(Block theBlock, Player player) {
        super(theBlock, player);
    }
}