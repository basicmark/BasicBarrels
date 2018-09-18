package io.github.basicmark.basicbarrels.block;

import io.github.basicmark.basicbarrels.BasicBarrels;
import io.github.basicmark.extendminecraft.ExtendMinecraft;
import io.github.basicmark.extendminecraft.block.ExtendBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class BarrelConduit extends ExtendBlock {
    /* Static (global) data */
    final static Set<BlockFace> connectableFaces = new HashSet<BlockFace>();

    /* These members persist through block load/unload */
    protected int level;
    protected int controllerChunkX;
    protected int controllerChunkZ;

    /* These members are dynamic are only live for the lifetime of the object */
    protected BarrelController linkedController;
    private Set<BarrelConduit> scanedConduits =  new HashSet<BarrelConduit>();
    private boolean isController = false;

    static {
        connectableFaces.add(BlockFace.NORTH);
        connectableFaces.add(BlockFace.EAST);
        connectableFaces.add(BlockFace.SOUTH);
        connectableFaces.add(BlockFace.WEST);
        connectableFaces.add(BlockFace.UP);
        connectableFaces.add(BlockFace.DOWN);
    }

    public BarrelConduit(Block block, String fullName) {
        super(block, fullName);
        level = 0;
        linkedController = null;
    }

    public void setController() {
        isController = true;
        level = 6;
        linkedController = (BarrelController) this;
    }

    private boolean linkController(BarrelController newController, int newLevel) {
        Bukkit.getLogger().info("linkController(" + newController + "," + newLevel + ")");

        /* No change so return */
        if ((newLevel == level) && (newController == linkedController)) {
            Bukkit.getLogger().info("No change");
            return false;
        }

        /*
         * Set the new controller (even if null) and add the conduit to the controller,
         * update it before adding/removing incase that triggers further querying of
         * this conduit.
         */
        BarrelController oldController = linkedController;
        linkedController = newController;
        int oldLevel = level;
        level = newLevel;

        /* Remove the existing link if there is a change in controller */
        if (oldController != newController) {
            if (oldController != null) {
                Bukkit.getLogger().info("Remove from old controller");
                oldController.remove(this);
            }
            if (newController != null) {
                newController.add(this);
                Bukkit.getLogger().info("Add to new controller");
            }
        }

        /*
         * A change in controller or level requires a reset of the cached scan list
         * so we rescan them.
         */
        if ((oldController != newController) || (oldLevel != newLevel))  {
            scanedConduits.clear();
        }

        return true;
    }

    private void conduitUpdateAdjacent(Queue<BarrelConduit> adjConduits, int oldLevel, boolean controllerChange) {
        int newLevel = getLevel();
        boolean resetLink = false;
        for (BarrelConduit adjConduit : adjConduits) {
            int adjLevel = adjConduit.getLevel();

            if (oldLevel != newLevel) {
                if ((adjLevel < oldLevel) && (adjLevel > newLevel)) {
                    resetLink = true;
                }
            }
        }
        if (resetLink) {
            linkController(null, 0);
        }

        for (BarrelConduit adjConduit : adjConduits) {
            if ((newLevel != oldLevel) || controllerChange) {
                adjConduit.conduitScan();
            }
            if (adjConduit.getLevel() == 0) {
                adjConduit.conduitScan();
            }
        }
    }

    public void conduitScan() {
        ExtendMinecraft extendMinecraft = BasicBarrels.getExtendMinecraft();
        Block block = getBukkitBlock();
        BarrelController oldController = getController();
        BarrelController curController = null;
        int oldLevel = getLevel();
        int curLevel = 0;
        Queue<BarrelConduit> adjConduits = new LinkedList<BarrelConduit>();
        Bukkit.getLogger().info("conduitScan start (" + block.getX() + "," + block.getY() + "," + block.getZ() + ")" );
        Bukkit.getLogger().info("level = " + getLevel());

        /* First find all adjacent conduits keeping track of the the highest level */
        for (BlockFace face : connectableFaces) {
            Block conBlock = block.getRelative(face);
            ExtendBlock extConBlock = extendMinecraft.getBlock(conBlock);
            if (extConBlock instanceof BarrelConduit) {
                BarrelConduit adjConduit = (BarrelConduit) extConBlock;
                Bukkit.getLogger().info("Found conduit on face " + face.name() + "with level " + adjConduit.getLevel());
                if (adjConduit.getLevel() > 1) {
                    if (adjConduit.getLevel() > (curLevel - 1)) {
                        curController = adjConduit.getController();
                        curLevel = adjConduit.getLevel() - 1;
                    }
                }
                adjConduits.add(adjConduit);
            }
        }

        boolean updated = false;
        if (!isController) {
            updated = linkController(curController, curLevel);
        }
        if (updated | isController) {
            conduitUpdateAdjacent(adjConduits, oldLevel, oldController != getController());
        }
    }

    public void conduitRemove() {
        ExtendMinecraft extendMinecraft = BasicBarrels.getExtendMinecraft();
        Block block = getBukkitBlock();
        Queue<BarrelConduit> adjConduits = new LinkedList<BarrelConduit>();
        int oldLevel = getLevel();
        linkController(null, 0);

        for (BlockFace face : connectableFaces) {
            Block conBlock = block.getRelative(face);
            ExtendBlock extConBlock = extendMinecraft.getBlock(conBlock);
            if (extConBlock instanceof BarrelConduit) {
                BarrelConduit adjConduit = (BarrelConduit) extConBlock;
                adjConduits.add(adjConduit);
            }
        }
        conduitUpdateAdjacent(adjConduits, oldLevel, true);
    }

    public int getLevel() {
        return level;
    }

    public BarrelController getController() {
        return linkedController;
    }

    @Override
    public void save(ConfigurationSection config) {
        //config.set("level", level);
        //config.set("controllerChunkX", controllerChunkX);
        //config.set("controllerChunkZ", controllerChunkZ);
    }

    @Override
    public void load(ConfigurationSection config) {
       // level = config.getInt("level");
        //controllerChunkX = config.getInt("controllerChunkX");
        //controllerChunkZ = config.getInt("controllerChunkZ");
    }

    @Override
    public void postChunkLoad(){
        Block block = getBukkitBlock();
        int chunkOffsetX = block.getX() & 0xf;
        int chunkOffsetZ = block.getZ() & 0xf;

        /*
            The controller is responsible for re-scanning after chunk load to avoid
            all the conduits in a chunk scanning (as there is no order in which blocks
            have this method called.
            However, a barrel network can cross chunk boundaries so a conduit which is
            on a chunk boundary must scan as its controller could be in another chunk
            (which may or may not be loaded).
         */
        if ((chunkOffsetX == 0) || (chunkOffsetX == 15) || (chunkOffsetZ == 0) || (chunkOffsetZ == 15))
            this.conduitScan();
    }
}
