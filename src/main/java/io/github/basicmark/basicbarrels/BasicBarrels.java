package io.github.basicmark.basicbarrels;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;


import io.github.basicmark.basicbarrels.block.*;
import io.github.basicmark.basicbarrels.managers.BarrelConduitManager;
import io.github.basicmark.basicbarrels.managers.BarrelManager;
import io.github.basicmark.basicbarrels.managers.BarrelControllerManager;
import io.github.basicmark.extendminecraft.ExtendMinecraft;
import io.github.basicmark.extendminecraft.block.ExtendBlock;
import io.github.basicmark.extendminecraft.block.ExtendBlockFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/*
 * Extra Features:
 * - Allow barrels to store items when broken rather then dropping them
 * - Barrel controller
 */
public class BasicBarrels extends JavaPlugin {
    private static boolean logEnabled;
    private static FileWriter logWriter;
    private static BarrelConduitManager conduitManager;
    private static BarrelManager barrelManager;
    private static BarrelControllerManager controllerManager;
    private static ExtendMinecraft extendMinecraft;

    private Set<ExtendBlockFactory> factories = new HashSet<ExtendBlockFactory>();
	private YamlConfiguration languageConfig;

	private void loadConfig() {
		FileConfiguration config = getConfig();

		//manager.setDefaultLocking(config.getBoolean("lockonplace", true));
		logEnabled = config.getBoolean("enablelogging", true);
		List<String> matStrList = config.getStringList("blacklist");

        Barrel.blacklistReset();
		for (String matStr : matStrList) {
			Material mat =  Material.matchMaterial(matStr);
			if (mat != null)
                Barrel.blacklistAdd(mat);
			else
				getLogger().info("Failed to parse " + matStr + " in the black list");
		}
		getLogger().info(matStrList.size() + " items found in blacklist");

		String languageFileName = config.getString("languageFile", "en.yml");
		File languageFile = new File(getDataFolder() + File.separator + languageFileName);
        languageConfig = new YamlConfiguration();
        try {
            languageConfig.load(languageFile);
        } catch (Exception e) {
            getLogger().info("Failed to load language file, creating from defaults");
        }

        Reader defaultConfigStream = null; /* Get config defaults from jar */
        try {
            defaultConfigStream = new InputStreamReader(getResource(languageFileName), "UTF8"); /* Get the config.yml from jar */
        } catch (UnsupportedEncodingException e) {
            getLogger().severe("Failed to load defaults for language file " + languageFileName);
        }

        if (defaultConfigStream != null)
        {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultConfigStream); /* Load defaults */
            getLogger().info("Loaded defaults:" + defaultConfig.saveToString());
            languageConfig.setDefaults(defaultConfig); /* Set defaults */
            languageConfig.options().copyDefaults(true);
        }

        try {
            getLogger().info("Saving:" + languageConfig.saveToString());
            languageConfig.save(languageFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save language file");
        }
	}

	private void startLogger() {
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

	public void onEnable(){
        extendMinecraft = (ExtendMinecraft) getServer().getPluginManager().getPlugin("ExtendMinecraft");
        if (extendMinecraft == null) {
            getLogger().severe("Failed to find ExtendMinecraft plugin");
            return;
        }
		// Create/load the config file
		saveDefaultConfig();
		loadConfig();

        conduitManager = new BarrelConduitManager(this);
        barrelManager = new BarrelManager(this);
        controllerManager = new BarrelControllerManager(this);

        factories.add(new BarrelFactory());
        factories.add(new BarrelControllerFactory());
        factories.add(new BarrelConduitFactory());

        for (ExtendBlockFactory factory : factories) {
            extendMinecraft.blockRegistry.add(factory);
        }

		if (logEnabled) {
			startLogger();
		}

		if (logEnabled) {
			getLogger().info("Logger enabled");
		} else {
			getLogger().info("Logger disabled");
		}
	}

	public void onDisable(){
        controllerManager.shutdown();

        for (ExtendBlockFactory factory : factories) {
            extendMinecraft.blockRegistry.remove(factory);
        }
		if (logEnabled) {
			try {
				logWriter.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static ExtendMinecraft getExtendMinecraft() {
	    return extendMinecraft;
    }

    public static BarrelControllerManager getBarrelControllerManager() {
	    return controllerManager;
    }

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("managers")){
			if ((args.length == 1) && args[0].equals("?")) {
                sender.sendMessage(translateMessage("HELP_TITLE1"));
                sender.sendMessage(translateMessage("HELP_TITLE2"));
                if (sender.hasPermission("managers.player.unlock")) {
                    sender.sendMessage(translateMessage("HELP_UNLOCK"));
                }
                if (sender.hasPermission("managers.player.lock")) {
                    sender.sendMessage(translateMessage("HELP_LOCK"));
                }
                if (sender.hasPermission("managers.player.info")) {
                    sender.sendMessage(translateMessage("HELP_INFO"));
                }
                if (sender.hasPermission("managers.admin.reload")) {
                    sender.sendMessage(translateMessage("HELP_RELOAD"));
                }
                if (sender.hasPermission("managers.admin.debug")) {
                    sender.sendMessage(translateMessage("HELP_DEBUG"));
                }
				return true;
			} else if ((args.length == 1) && args[0].equals("unlock")) {
                if (!sender.hasPermission("managers.player.unlock")) {
                    sender.sendMessage(translateMessage("COMMAND_PERMISSION_ERROR"));
                    return true;
                }
                if (!(sender instanceof Player)) {
					sender.sendMessage(translateMessage("COMMAND_CONSOLE_SENDER_ERROR"));
					return true;
				}
				Player player = (Player) sender;
				barrelManager.setPendingRequest(player, BarrelManager.BarrelOperation.UNLOCK);
                sender.sendMessage(translateMessage("OPERATION_UNLOCK"));
				return true;	
			} else if ((args.length == 1) && args[0].equals("lock")) {
                if (!sender.hasPermission("managers.player.lock")) {
                    sender.sendMessage(translateMessage("COMMAND_PERMISSION_ERROR"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(translateMessage("COMMAND_CONSOLE_SENDER_ERROR"));
                    return true;
                }
				Player player = (Player) sender;
                barrelManager.setPendingRequest(player, BarrelManager.BarrelOperation.LOCK);
                sender.sendMessage(translateMessage("OPERATION_LOCK"));
				return true;	
			} else if ((args.length == 1) && args[0].equals("info")) {
                if (!sender.hasPermission("managers.player.info")) {
                    sender.sendMessage(translateMessage("COMMAND_PERMISSION_ERROR"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(translateMessage("COMMAND_CONSOLE_SENDER_ERROR"));
                    return true;
                }
                Player player = (Player) sender;
                barrelManager.setPendingRequest(player, BarrelManager.BarrelOperation.INFO);
                sender.sendMessage(translateMessage("OPERATION_INFO"));
                return true;
            } else if ((args.length == 1) && args[0].equals("reload")) {
                if (!sender.hasPermission("managers.admin.reload")) {
                    sender.sendMessage(translateMessage("COMMAND_PERMISSION_ERROR"));
                    return true;
                }

				loadConfig();
                sender.sendMessage(translateMessage("CONFIG_RELOADED"));
				return true;
            }  else if ((args.length == 1) && args[0].equals("debug")) {
                if (!sender.hasPermission("managers.admin.debug")) {
                    sender.sendMessage(translateMessage("COMMAND_PERMISSION_ERROR"));
                    return true;
                }

                //manager.debugInfo(sender);
                return true;
            }
		}
		return false;
	}

    public String translateMessage(String messageID) {
        return translateMessage(messageID, null);
    }

	public String translateMessage(String messageID, Map<String, String> substitutes) {
	    String ret = languageConfig.getString(messageID, "Unknown message ID");
	    if (substitutes != null) {
            for (String key : substitutes.keySet()) {
                ret = ret.replaceAll(key, substitutes.get(key));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', ret);
    }
	
	public static void logEvent(String event) {
		if (logEnabled) {
            SimpleDateFormat timeStamp = new SimpleDateFormat("HH:mm:ss");
			try {
				logWriter.append("[" + timeStamp.format(new Date()) + "] " + event + System.getProperty("line.separator"));
				logWriter.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Bukkit.getServer().getLogger().info(event);
	}
}

