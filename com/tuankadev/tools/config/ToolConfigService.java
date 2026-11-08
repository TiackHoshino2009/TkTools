package com.tuankadev.tools.config;

import com.tuankadev.tools.TktoolsUpdate;
import com.tuankadev.tools.tool.ToolDefinition;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

public final class ToolConfigService {
   private final TktoolsUpdate plugin;
   private final Map<String, ToolDefinition> tools = new HashMap();

   public ToolConfigService(TktoolsUpdate plugin, YamlConfiguration config) {
      this.plugin = plugin;
      ConfigurationSection section = config.getConfigurationSection("tools");
      if (section != null) {
         for(String id : section.getKeys(false)) {
            ConfigurationSection toolSection = section.getConfigurationSection(id);
            if (toolSection != null) {
               Material material = Material.matchMaterial(toolSection.getString("material", "STONE"));
               if (material == null) {
                  plugin.getLogger().warning("Invalid material for tool " + id);
               } else {
                  String name = toolSection.getString("name", id);
                  List<String> lore = new ArrayList(toolSection.getStringList("lore"));
                  Map<Enchantment, Integer> enchants = new HashMap();
                  ConfigurationSection enchantSection = toolSection.getConfigurationSection("enchants");
                  if (enchantSection != null) {
                     for(String enchantKey : enchantSection.getKeys(false)) {
                        Enchantment enchantment = this.resolveEnchantment(enchantKey);
                        if (enchantment == null) {
                           plugin.getLogger().warning("Unknown enchantment '" + enchantKey + "' for tool " + id);
                        } else {
                           enchants.put(enchantment, enchantSection.getInt(enchantKey));
                        }
                     }
                  }

                  this.tools.put(id.toLowerCase(Locale.ROOT), new ToolDefinition(id.toLowerCase(Locale.ROOT), material, name, lore, enchants));
               }
            }
         }
      }

   }

   public ToolDefinition getTool(String id) {
      return (ToolDefinition)this.tools.get(id.toLowerCase(Locale.ROOT));
   }

   public Collection<String> getToolIds() {
      return Collections.unmodifiableSet(this.tools.keySet());
   }

   private Enchantment resolveEnchantment(String input) {
      String normalized = input.toLowerCase(Locale.ROOT).trim();
      NamespacedKey key = normalized.contains(":") ? NamespacedKey.fromString(normalized) : NamespacedKey.minecraft(normalized);
      Enchantment enchantment = this.resolveViaRegistry(key);
      if (enchantment != null) {
         return enchantment;
      } else {
         if (key != null) {
            try {
               Enchantment byKey = Enchantment.getByKey(key);
               if (byKey != null) {
                  return byKey;
               }
            } catch (Throwable var8) {
            }
         }

         String legacy = normalized.toUpperCase(Locale.ROOT).replace(':', '_');

         try {
            return Enchantment.getByName(legacy);
         } catch (Throwable var7) {
            this.plugin.getLogger().warning("Failed to resolve enchantment '" + input + "' using registry, key, or legacy lookup.");
            return null;
         }
      }
   }

   private Enchantment resolveViaRegistry(NamespacedKey key) {
      if (key == null) {
         return null;
      } else {
         try {
            Field field = Registry.class.getField("ENCHANTMENT");
            Object registry = field.get((Object)null);
            Method getMethod = registry.getClass().getMethod("get", NamespacedKey.class);
            Object var6 = getMethod.invoke(registry, key);
            Enchantment var10000;
            if (var6 instanceof Enchantment) {
               Enchantment enchantment = (Enchantment)var6;
               var10000 = enchantment;
            } else {
               var10000 = null;
            }

            return var10000;
         } catch (Throwable var7) {
            return null;
         }
      }
   }
}
