package io.github.basicmark.basicbarrels;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public enum BarrelType {
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

    static public BarrelType itemStackBarrelType(ItemStack item, int nameOffset) {
        ItemMeta data = item.getItemMeta();
        if (data != null) {
            if (data.hasLore()) {
                for (BarrelType type : BarrelType.values()) {
                    List<String> loreText = data.getLore();
                    if (loreText.size() > nameOffset) {
                        if (loreText.get(nameOffset).equals(type.getName()))
                            return type;
                    }
                }
            }
        }
        /* TODO: Handle failure  */
        return IRON_BARREL;
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