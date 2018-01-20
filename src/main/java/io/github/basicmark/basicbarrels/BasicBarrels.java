package io.github.basicmark.basicbarrels;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.EntityItemFrame;
import net.minecraft.server.v1_12_R1.EnumDirection;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;


/*
 * Extra Features:
 * - Command to show the owner
 * - Custom messages
 * - Allow barrels to store items when broken rather then dropping them
 * - Black/White list items which can be stored
 */
public class BasicBarrels extends JavaPlugin implements Listener {
	static EnumSet<BarrelType> barrelSet = EnumSet.allOf(BarrelType.class);
	static EnumSet<Material> logTypeSet = EnumSet.of(Material.LOG, Material.LOG_2);
	static Integer maxDataValue[] = {4,2};
	static final BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
	static final String barrelFrameName = "BasicBarrel";
	/* Lore text offsets */
	static final int versionOffset = 0;
	/* Version 1 offsets */
	static final int typeOffset = 1;
	static final int amountOffset = 2;
	static final int ownerUUIDOffset = 3;
	static final int blockDataOffset = 4;
	/*
	 * Version of data offsets we're using.
	 * 
	 * Chances are we might need to extend the loreText pre-amble
	 * so we version our data so we can support backwards compatibility.
	 */
	static final int dataVersion = 1;

	ItemStack empty;
	Set<Recipe> recipes;
	Map<Player, BarrelOperation> playerOperation;
	Set<Material> blacklist;
	boolean lockOnPlace;
	boolean logEnabled;
	FileWriter logWriter;

	public void loadConfig() {
		FileConfiguration config = getConfig();

		lockOnPlace = config.getBoolean("lockonplace", true);
		logEnabled = config.getBoolean("enablelogging", true);
		blacklist = new HashSet<Material>();
		List<String> matStrList = config.getStringList("blacklist");

		for (String matStr : matStrList) {
			Material mat =  Material.matchMaterial(matStr);
			if (mat != null)
				blacklist.add(mat);
			else
				getLogger().info("Failed to parse " + matStr + " in the black list");
		}
		getLogger().info(blacklist.size() + " items found in blacklist");
	}

	public void onEnable(){
		// Create/load the config file
		saveDefaultConfig();
		loadConfig();
		
		if (logEnabled) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
			String date = dateFormat.format(new Date());
			String fileName = "";
			boolean logSlotFound = false;

			/* Try and find the next available log name */
			for (int i=0;i<100;i++) {
				fileName = this.getDataFolder() + File.separator + date + "-" + i + ".log";

				if (! new File(fileName).isFile()) {
					logSlotFound = true;
					break;
				}
			}
			
			/* Create the writer if we found a free log */
			if (logSlotFound) {
				try {
					logWriter = new FileWriter(fileName);
				} catch (IOException e) {
					getLogger().info("Failed to open log, disabling logging");
					e.printStackTrace();
					logEnabled = false;
				}
			} else {
				getLogger().info("Failed to find a free lot slot, disabling logging");
				logEnabled = false;
			}
		}

		if (logEnabled) {
			getLogger().info("Logger enabled");
		} else {
			getLogger().info("Logger disabled");
		}
		
		playerOperation = new HashMap<Player, BarrelOperation>();
		recipes = new HashSet<Recipe>();
		/* Register the different types of barrels that can be crafted */
		for (BarrelType barrel : barrelSet) {
			Integer dataTypeIndex = 0;
			for (Material log : logTypeSet) {
				for (byte dataValue = 0; dataValue < maxDataValue[dataTypeIndex]; dataValue++) {
					/* Create the ItemStack we wish to produce */
					ItemStack item = new ItemStack(log, 1, (short) 0, dataValue);
					ItemMeta data = item.getItemMeta();
					data.setDisplayName(barrel.getName());
					List<String> loreText = new ArrayList<String>();

					loreText.add(((Integer)dataVersion).toString());
					loreText.add(barrel.getName());
					data.setLore(loreText);
					item.setItemMeta(data);

					/* Then create the recipe */
					ShapedRecipe recipe = new ShapedRecipe(item);

					recipe.shape("EME","MLM","EME");
					recipe.setIngredient('L', log, dataValue);
					recipe.setIngredient('M', barrel.getMaterial());
					recipe.setIngredient('E', Material.ENDER_PEARL);

					/* Lastly add the recipe */
					getServer().addRecipe(recipe);
					recipes.add(recipe);
				}
			}
		}

		empty = new ItemStack(Material.BARRIER);
		ItemMeta meta = empty.getItemMeta();
		meta.setDisplayName("Empty");
		empty.setItemMeta(meta);
		getServer().getPluginManager().registerEvents(this, this);
	}

	public void onDisable(){
		if (logEnabled) {
			try {
				logWriter.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("barrel")){
			if ((args.length == 1) && args[0].equals("?")) {
				sender.sendMessage("BasicBarrels help");
				sender.sendMessage("=================");
				sender.sendMessage("/barrel unlock :- Unlock a barrel");
				sender.sendMessage("/barrel lock :- Lock a barrel");
				sender.sendMessage("/barrel reload :- Reload configuration files");
				return true;
			} else if ((args.length == 1) && args[0].equals("unlock")) {
				if (!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "Only players can use this command!");
				}
				Player player = (Player) sender;
				playerOperation.put(player, BarrelOperation.UNLOCK);
				player.sendMessage("Punch the barrels itemframe to unlock it");
				return true;	
			} else if ((args.length == 1) && args[0].equals("lock")) {
				if (!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "Only players can use this command!");
				}
				Player player = (Player) sender;
				playerOperation.put(player, BarrelOperation.LOCK);
				player.sendMessage("Punch the barrels itemframe to lock it");
				return true;	
			} else if ((args.length == 1) && args[0].equals("reload")) {
				if (sender.hasPermission("barrel.reload")) {
					loadConfig();
					sender.sendMessage("Config reloaded");
				} else {
					sender.sendMessage(ChatColor.RED + "You don't have permissions for this command");
				}
				return true;
			}
		}
		return false;
	}

	private boolean hasPermission(ItemFrame itemFrame, Player player) {
		ItemMeta meta = itemFrame.getItem().getItemMeta();
		List<String> loreText = meta.getLore();

		if (!player.hasPermission("barrel.use")) {
			player.sendMessage("You don't have permission!");
			return false;
		}

		if (loreText.get(ownerUUIDOffset).equals("none")) {
			return true;
		}

		if (loreText.get(ownerUUIDOffset).equals(player.getUniqueId().toString())) {
			return true;
		}

		if (player.hasPermission("barrel.use_others")) {
			return true;
		}

		player.sendMessage("Your don't have permission to use that barrel");
		return false;
	}
	
	private boolean isStoreable(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		if (meta.hasDisplayName() || meta.hasEnchants() || meta.hasLore())
			return false;
		if (blacklist.contains(item.getType()))
			return false;

		return true;
	}

	private boolean matchsBarrelItem(ItemStack barrelItem, ItemStack addItem) {
		if (addItem == null) {
			return false;
		}

		/* Items which contain data which can't be stored will not match */
		if (!isStoreable(addItem)) {
			return false;
		}
		
		if (addItem.getType() != barrelItem.getType()) {
			return false;
		}

		if (barrelItem.getType().equals(Material.POTION) || barrelItem.getType().equals(Material.SPLASH_POTION) ||  barrelItem.getType().equals(Material.LINGERING_POTION) | barrelItem.getType().equals(Material.TIPPED_ARROW)) {
			PotionMeta barrelMeta = (PotionMeta) barrelItem.getItemMeta();
			PotionMeta addMeta = (PotionMeta) addItem.getItemMeta();
			PotionData barrelData = barrelMeta.getBasePotionData();
			PotionData addData = addMeta.getBasePotionData();

			return barrelData.equals(addData);
		} else {
			/* Check the item stacks attributes for a match*/
			return 	(addItem.getDurability() == barrelItem.getDurability()) &&
					(addItem.getData().getData() == barrelItem.getData().getData());
		}
	}

	private void updateAmount(ItemFrame itemFrame, ItemStack barrelItem, ItemMeta barrelMeta, List<String> loreText, Integer amount) {
		if (logEnabled) {
			logEvent(itemFrame, "Barrel containing " + barrelItem.getType() + " updated from " + loreText.get(amountOffset) + " to " + amount.toString());
		}
		loreText.set(amountOffset, amount.toString());
		Integer stackSize = barrelItem.getMaxStackSize();
		Integer remain = amount % stackSize;
		amount = (amount - remain) / stackSize;
		barrelMeta.setDisplayName(amount.toString() + "*" + stackSize + "+" + remain);
		barrelMeta.setLore(loreText);
		barrelItem.setItemMeta(barrelMeta);
	}
	

	private ItemFrame getBarrelFrame(Block block) {
		World world = block.getWorld();
		Collection<ItemFrame> entites = world.getEntitiesByClass(ItemFrame.class);
		String searchName = barrelFrameName + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
		for (ItemFrame itemFrame : entites) {
			if (searchName.equals(itemFrame.getCustomName())) {
				return itemFrame;
			}
		}
		return null;
	}
	
	private Block getBarrelBlock(ItemFrame itemFrame) {
		String nameParts[] = itemFrame.getCustomName().split("_");
		Integer X = Integer.parseInt(nameParts[1]);
		Integer Y = Integer.parseInt(nameParts[2]);
		Integer Z = Integer.parseInt(nameParts[3]);
		World world = itemFrame.getWorld();
		return world.getBlockAt(new Location(world, X, Y, Z));
	}

	private boolean isBarrelFrameLocation(Location location) {
		World world = location.getWorld();
		Entity entites[] = world.getChunkAt(location).getEntities();
		for (Entity entity : entites) {
			if (entity.getType() == EntityType.ITEM_FRAME) {
				ItemFrame itemFrame = (ItemFrame) entity;
				if ((itemFrame.getCustomName() != null) && itemFrame.getCustomName().contains(barrelFrameName)) {
					Location frameLoc = itemFrame.getLocation();

					if ((frameLoc.getBlockX() == location.getBlockX()) &&
							(frameLoc.getBlockY() == location.getBlockY()) &&
							(frameLoc.getBlockZ() == location.getBlockZ())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void breakBarrel(ItemFrame itemFrame) {
		breakBarrel(getBarrelBlock(itemFrame), itemFrame, GameMode.SURVIVAL);
	}

	private void breakBarrel(Block block, ItemFrame itemFrame, GameMode mode) {
		World world = block.getWorld();
		
		/* Dump the barrel contents on the ground. */
		ItemStack frameItem = itemFrame.getItem();
		List<String> loreText = frameItem.getItemMeta().getLore();
		Integer amount = Integer.parseInt(loreText.get(amountOffset));
		logEvent(itemFrame, "Barrel broken dropping " + amount + " of " + frameItem.getType());
		while (amount != 0) {
			Integer dropCount = Math.min(amount, frameItem.getMaxStackSize());

			ItemStack dropItem;
			if (frameItem.getType().equals(Material.POTION) || frameItem.getType().equals(Material.SPLASH_POTION) ||  frameItem.getType().equals(Material.LINGERING_POTION) || frameItem.getType().equals(Material.TIPPED_ARROW)) {
				dropItem = new ItemStack(frameItem.getType(), dropCount);
				PotionMeta pMeta = (PotionMeta) frameItem.getItemMeta();
				pMeta.setDisplayName(null);
				pMeta.setLore(null);
				dropItem.setItemMeta(pMeta);
			} else {
				dropItem = new ItemStack(frameItem.getType(), dropCount, frameItem.getDurability(),  frameItem.getData().getData());
				dropItem.setData(frameItem.getData());
				dropItem.setDurability(frameItem.getDurability());
			}			
			world.dropItemNaturally(block.getLocation(), dropItem);
			amount -= dropCount;
		}

		if (mode != GameMode.CREATIVE) {
			/* Then drop the barrel itself */
			String blockData[] = loreText.get(blockDataOffset).split(":");
			
			ItemStack dropBarrel = new ItemStack(Material.getMaterial(blockData[0]), 1, (short) 0, Byte.parseByte(blockData[1]));
			ItemMeta dropBarrelMeta = dropBarrel.getItemMeta();
			dropBarrelMeta.setLore(loreText.subList(versionOffset, typeOffset + 1));
			dropBarrelMeta.setDisplayName(loreText.get(typeOffset));
			dropBarrel.setItemMeta(dropBarrelMeta);
			world.dropItemNaturally(block.getLocation(), dropBarrel);
		}

		itemFrame.remove();

		/* Remove the barrel block  */
		BlockState state = block.getState();
		state.setType(Material.AIR);
		state.update(true);
	}
	
	private void logEvent(ItemFrame itemFrame, String event) {
		String location[] = itemFrame.getCustomName().split("_");
		String eventPrefix = itemFrame.getWorld().getName() + "," + location[1] + "," + location[2] + "," + location[3] + ": ";
		SimpleDateFormat timeStamp = new SimpleDateFormat("HH:mm:ss");
		if (logEnabled) {
			try {
				logWriter.append("[" + timeStamp.format(new Date()) + "] " + eventPrefix + event + System.getProperty("line.separator"));
				logWriter.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@EventHandler
	public void onPrepareItemCraftEvent(PrepareItemCraftEvent event) {
		/* Check if the player is allowed to craft a barrel */
		if ((recipes.contains(event.getRecipe()) && (!event.getView().getPlayer().hasPermission("barrel.use")))) {
			event.getInventory().setResult(null);
		}

		/* Stop players from crafting with barrels */
		ItemStack items[] = event.getInventory().getMatrix();
		for (ItemStack item : items) {
			if ((item != null) && (logTypeSet.contains(item.getType()))) {
				if (BarrelType.itemStackIsBarrel(item, typeOffset)) {
					event.getInventory().setResult(null);
				}
			}
		}
	}
	
	@EventHandler
	public void onBlockPlaceEvent(BlockPlaceEvent event) {
		/* Don't process our dummy events */
		if (event instanceof BasicBarrelBlockPlaceEvent) {
			return;
		}

		/* Check the block isn't going to obstruct the item frame */
		if (isBarrelFrameLocation(event.getBlock().getLocation())) {
			event.setCancelled(true);
			return;
		}
		
		ItemStack item = event.getItemInHand();
		/* Detect if a barrel is being placed */
		if (BarrelType.itemStackIsBarrel(item, typeOffset)) {
			Block block = event.getBlockPlaced();
			Player player = event.getPlayer();

			if (!player.hasPermission("barrel.use")) {
				player.sendMessage("You don't have permission!");
				event.setCancelled(true);
				return;
			}

			World world = player.getWorld();
			BlockFace face = faces[Math.round(player.getLocation().getYaw() / 90f) & 3];
			Block inFrontBlock = block.getRelative(face);
			Location inFrontLoc = block.getRelative(face).getLocation();

			/* Barrels need a space in front of them */
			if (inFrontBlock.getType() != Material.AIR) {
				player.sendMessage("There must be an air block infront of the barrel");
				event.setCancelled(true);
				return;
			}

			/* Create a dummy event to check that another plug-in wouldn't cancel it */
			BasicBarrelBlockPlaceEvent customEvent = new BasicBarrelBlockPlaceEvent(event.getBlock(),
					event.getBlockReplacedState(), event.getBlockAgainst(),
					event.getItemInHand(), player, true);
			getServer().getPluginManager().callEvent(customEvent);
			if (customEvent.isCancelled()) {
				event.setCancelled(true);
				return;
			}

			/*
			 * #BukkitIsBroken
			 *
			 * 1) You can't set the direction an item frame is facing when it
			 * spawns.
			 * 2) The block on which the item frame is facing must be solid or
			 * the spawn will throw an exception.
			 * 3) The location specified in the spawn call is the block the itemfame
			 * is in rather then the block it's trying to hang from.
			 * 4) The blocks around the item frame effect which block it will hang on,
			 * and indeed if it hang or throw an abort even through there is a viable
			 * block next to its location.
			 * 5) setFacingDirection changes to location of the itemframe to by over a block
			 * rather then rotating it within the same "block space".
			 * 
			 * For these reasons we have to resort to using NMS calls to create the itemframe
			 * which (ignoring the casting and datatype conversions) makes spawning it trivial.
			 * 
			 */

			EnumDirection mcDir = EnumDirection.NORTH;
			switch(face) {
			case NORTH:
				mcDir = EnumDirection.NORTH;
				break;
			case SOUTH:
				mcDir = EnumDirection.SOUTH;
				break;
			case EAST:
				mcDir = EnumDirection.EAST;
				break;
			case WEST:
				mcDir = EnumDirection.WEST;
				break;
			default:
				getLogger().info("Bad direction: " + face);
			}

			net.minecraft.server.v1_12_R1.World mcWorld = ((CraftWorld)world).getHandle();
			net.minecraft.server.v1_12_R1.Entity entity = new EntityItemFrame(mcWorld, new BlockPosition(inFrontLoc.getX(), inFrontLoc.getY(), inFrontLoc.getZ()), mcDir);
			mcWorld.addEntity(entity, SpawnReason.CUSTOM);
            ItemFrame bukkitItemFrame = (ItemFrame) entity.getBukkitEntity();
            ItemStack barrelItem = empty.clone();

			ItemMeta barrelMeta = barrelItem.getItemMeta();
			List<String> loreText = new ArrayList<String>();
			/* 
			 * Create the Lore data store:
			 * 
			 * Data version
			 * Type
			 * Amount
			 * ownerUUID
			 * blockData
			 */
			loreText.add(((Integer)dataVersion).toString());
			loreText.add(item.getItemMeta().getLore().get(typeOffset));
			loreText.add("0");
			if (lockOnPlace)
				loreText.add(player.getUniqueId().toString());
			else
				loreText.add("none");
			loreText.add(item.getType() + ":" + item.getData().getData());
			barrelMeta.setLore(loreText);
			barrelItem.setItemMeta(barrelMeta);		

            bukkitItemFrame.setItem(barrelItem);
            bukkitItemFrame.setCustomName(barrelFrameName + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
            logEvent(bukkitItemFrame, "Barrel placed");
		}
	}
	
	@EventHandler
	public void onBlockBreakEvent(BlockBreakEvent event) {
		Block block = event.getBlock();

		/* Don't process our dummy events */
		if (event instanceof BasicBarrelBlockBreakEvent) {
			return;
		}
		
		/* Filter out no barrel block types */
		if (!logTypeSet.contains(block.getType())) {
			return;
		}

		/* Barrel item frame store the location of the barrel block, no item frame means no barrel */
		ItemFrame itemFrame = getBarrelFrame(block);
		if (itemFrame == null) {
			return;
		}

		/* Cancel regardless of if the player has perms or not */
		event.setCancelled(true);
		if (!hasPermission(itemFrame, event.getPlayer())) {
			return;
		}

		/* Create a dummy event to check that another plug-in wouldn't cancel it */
		BasicBarrelBlockBreakEvent customEvent = new BasicBarrelBlockBreakEvent(block, event.getPlayer());
		getServer().getPluginManager().callEvent(customEvent);
		if (customEvent.isCancelled()) {
			event.setCancelled(true);
			return;
		}

		breakBarrel(block, itemFrame, event.getPlayer().getGameMode());
	}
	
	@EventHandler
	public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent event) {
		Entity entity = event.getRightClicked();
		Player player = event.getPlayer();

		/* Add items */
		if ((entity.getCustomName() != null) && entity.getCustomName().contains(barrelFrameName)) {
			/* What ever happens we want to stop the event */
			event.setCancelled(true);

			/* You must have an item in your hand! */
			if (player.getItemInHand() == null) {
				return;
			}

			if (player.getItemInHand().getType() == Material.AIR) {
				return;
			}

			ItemFrame itemFrame = (ItemFrame) entity;
			if (!hasPermission(itemFrame, player)) {
				return;
			}

			ItemStack barrelItem = itemFrame.getItem();
			ItemMeta barrelMeta = barrelItem.getItemMeta();
			ItemStack addItem = player.getItemInHand();
			Boolean barrelEmpty = true;

			/* Fast check, are we empty? */
			if (!barrelItem.getItemMeta().getDisplayName().equals(empty.getItemMeta().getDisplayName())) {
				barrelEmpty = false;
			}

			/* empty -> not empty transition, clone the input item but copy the old lore data */
			if (barrelEmpty) {
				if (!isStoreable(addItem)) {
					player.sendMessage("Sorry, you can't store that item in the barrel");
					return;
				}
				barrelItem = addItem.clone();
				ItemMeta newBarrelMeta = barrelItem.getItemMeta();
				newBarrelMeta.setLore(barrelMeta.getLore());
				barrelMeta = newBarrelMeta;
			}

			/* Now add the new items to the barrel and remove them from the player */
			List<String> loreText = barrelMeta.getLore();
			PlayerInventory inventory = player.getInventory();
			Integer amount = Integer.parseInt(loreText.get(amountOffset));
			Integer max = BarrelType.fromName(loreText.get(typeOffset)).getSize() * barrelItem.getMaxStackSize();
			Integer i, k;

			if (player.isSneaking()) {
				/* Add all the matching items in the players inventory */
				i = 0;
				k = inventory.getSize();
			} else {
				/* Add just the stack in the hand */
				i = inventory.getHeldItemSlot();
				k = i + 1;
			}

			for (; i<k;i++) {
				addItem = inventory.getItem(i);
				if (matchsBarrelItem(barrelItem, addItem)) {
					Integer toAdd = addItem.getAmount();
					if ((amount + toAdd) > max) {
						Integer remove = (amount + toAdd) - max;
						amount = max;
						addItem.setAmount(remove);
					} else {
						amount += addItem.getAmount();
						inventory.setItem(i, null);
					}
				}
			}
			updateAmount(itemFrame, barrelItem, barrelMeta, loreText, amount);			
			itemFrame.setItem(barrelItem);
		}
	}
	
	@EventHandler
	public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
		Entity damager = event.getDamager();
		Entity entity = event.getEntity();

		if ((event.getEntity().getCustomName() != null) && event.getEntity().getCustomName().contains(barrelFrameName)) {
			/* What ever happens we want to stop the event */
			event.setCancelled(true);

			/* Only players can interact with barrels */
			if (!(damager instanceof Player)) {
				return;
			}
			Player player = (Player) damager;

			ItemFrame itemFrame = (ItemFrame) entity;
			if (!hasPermission(itemFrame, player)) {
				return;
			}

			ItemStack barrelItem = itemFrame.getItem();
			ItemMeta barrelMeta = barrelItem.getItemMeta();
			List<String> loreText = barrelMeta.getLore();

			/* Check if the player has a pending operation */
			if (playerOperation.containsKey(player)) {
				
				switch (playerOperation.get(player)) {
				case LOCK:
					loreText.set(ownerUUIDOffset, player.getUniqueId().toString());
					player.sendMessage("Barrel locked");
					break;
				case UNLOCK:
					loreText.set(ownerUUIDOffset, "none");
					player.sendMessage("Barrel unlocked");
					break;
				}
				playerOperation.remove(player);

				/* Update the barrel */
				barrelMeta.setLore(loreText);
				barrelItem.setItemMeta(barrelMeta);
				itemFrame.setItem(barrelItem);
				return;
			}
			
			/* Fast check, are we empty? */
			if (barrelItem.getItemMeta().getDisplayName().equals(empty.getItemMeta().getDisplayName())) {
				return;
			}
			Integer amount = Integer.parseInt(loreText.get(amountOffset));
			Integer take = 1;
			if (!player.isSneaking()) {
				take = barrelItem.getMaxStackSize();
			}
			take = Math.min(take, amount);
			amount -= take;

			/* Give the player the items they've space for */
			if (take > 0) {
				ItemStack give;
				if (barrelItem.getType().equals(Material.POTION) || barrelItem.getType().equals(Material.SPLASH_POTION) ||  barrelItem.getType().equals(Material.LINGERING_POTION) || barrelItem.getType().equals(Material.TIPPED_ARROW)) {
					give = new ItemStack(barrelItem.getType(), take);
					PotionMeta pMeta = (PotionMeta) barrelItem.getItemMeta();
					pMeta.setDisplayName(null);
					pMeta.setLore(null);
					give.setItemMeta(pMeta);
				} else {
					give = new ItemStack(barrelItem.getType(), take, barrelItem.getDurability(),  barrelItem.getData().getData());
					give.setData(barrelItem.getData());
					give.setDurability(barrelItem.getDurability());
				}

				HashMap<Integer, ItemStack> remain = player.getInventory().addItem(give);
				for (ItemStack reAdd : remain.values()) {
					amount += reAdd.getAmount();
				}
				updateAmount(itemFrame, barrelItem, barrelMeta, loreText, amount);
			}

			/* not empty -> empty transition so reset it to the empty item but keeping the lore data */
			if (amount == 0) {
				barrelItem = empty.clone();
				ItemMeta newBarrelMeta = barrelItem.getItemMeta();
				newBarrelMeta.setLore(barrelMeta.getLore());
				barrelItem.setItemMeta(newBarrelMeta);
				logEvent(itemFrame, "Barrel is now empty");
			}
			itemFrame.setItem(barrelItem);
		}
	}
	
	@EventHandler
	public void onEntityDamageEvent(EntityDamageEvent event) {
		/* Stop other forms of damage from effecting barrel itemframes */
		if ((event.getEntity().getCustomName() != null) && event.getEntity().getCustomName().contains(barrelFrameName)) {
			if (event.getCause() != DamageCause.ENTITY_ATTACK) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onHangingBreakEvent(HangingBreakEvent event) {
		/* Detect if a barrel is being broken */
		if ((event.getEntity().getCustomName() != null) && event.getEntity().getCustomName().contains(barrelFrameName) && (event.getEntity() instanceof ItemFrame)) {
			/* Either cancel the event or break the barrel */
			ItemFrame itemFrame = (ItemFrame) event.getEntity();
			Block block = getBarrelBlock(itemFrame);

			/* Only drop the barrel when the itemframe has nothing to hang from. */
			if (logTypeSet.contains(block.getType())) {
				event.setCancelled(true);
			} else {
				breakBarrel(itemFrame);
			}
		}
	}

	public class BasicBarrelBlockBreakEvent extends BlockBreakEvent {
		public BasicBarrelBlockBreakEvent(Block theBlock, Player player) {
			super(theBlock, player);
		}
	}
	
	public class BasicBarrelBlockPlaceEvent extends BlockPlaceEvent {
		public BasicBarrelBlockPlaceEvent(Block placedBlock,
				BlockState replacedBlockState, Block placedAgainst,
				ItemStack itemInHand, Player thePlayer, boolean canBuild) {
			super(placedBlock, replacedBlockState, placedAgainst, itemInHand, thePlayer,
					canBuild);
		}
	}

	private enum BarrelType {
		IRON_BARREL("Iron barrel", 64, Material.IRON_INGOT), GOLD_BARREL("Gold barrel", 1024, Material.GOLD_INGOT), DIAMOND_BARREL("Diamond barrel", 4096, Material.DIAMOND);
		private final String name;
		private final int size;
		private final Material material;

		private BarrelType(String name, int size, Material material) {
			this.name = name;
			this.size = size;
			this.material = material;
		}

		public String getName() {
			return name;
		}

		public int getSize() {
			return size;
		}

		public Material getMaterial() {
			return material;
		}

		static public Boolean itemStackIsBarrel(ItemStack item, int nameOffset) {
			ItemMeta data = item.getItemMeta();
			if (data != null) {
				if (data.hasLore()) {
					for (BarrelType type : BarrelType.values()) {
						List<String> loreText = data.getLore();
						if (loreText.size() > nameOffset) {
							if (loreText.get(nameOffset).equals(type.getName()))
								return true;
						}
					}
				}
			}

			return false;
		}
		
		static public BarrelType fromName(String name) {
			for (BarrelType type : BarrelType.values()) {
				if (type.getName().equals(name)) {
					return type;
				}
			}
			/* TODO: throw some form of error */
			return BarrelType.IRON_BARREL;
		}
	}

	private enum BarrelOperation {
		LOCK, UNLOCK;
	}
}

