package io.github.basicmark.basicbarrels;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class Utils {
    public static Block getBlockRelativeIfLoaded(Block block, BlockFace face) {
        int x = block.getX();
        int z = block.getZ();

        /* Is the block on a chunk boundary? */
        if ((((x & 0xf) == 0) || ((x & 0xf) == 15)) || (((z & 0xf) == 0) || ((z & 0xf) == 15))) {
            Integer[] relLoc = getBlockRelativeChunk(block, face);
            /* Shift to obtain chunk location from block location */
            x >>= 4;
            z >>= 4;

            /* Are the input and relative block within the same chunk? */
            if ((relLoc[0] != x) || (relLoc[1] != z )) {
                /* The input and relative block are in different chunks so check if the relative chunk is loaded */
                if (!block.getWorld().isChunkLoaded(relLoc[0], relLoc[1])) {
                    return null;
                }
            }
        }
        return block.getRelative(face);
    }

    public static Integer[] getBlockRelativeChunk(Block block, BlockFace face) {
        int x = block.getX() >> 4;
        int z = block.getZ() >> 4;

        switch (face) {
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
}
