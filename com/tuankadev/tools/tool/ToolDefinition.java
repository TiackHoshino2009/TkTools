package com.tuankadev.tools.tool;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

public record ToolDefinition(String id, Material material, String name, List<String> lore, Map<Enchantment, Integer> enchants) {
}
