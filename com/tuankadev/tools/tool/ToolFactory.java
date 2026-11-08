package com.tuankadev.tools.tool;

import com.tuankadev.tools.service.RuntimeClockService;
import com.tuankadev.tools.util.ColorUtil;
import com.tuankadev.tools.util.DurationFormatter;
import de.tr7zw.nbtapi.NBT;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ToolFactory {
   private ToolFactory() {
   }

   public static ItemStack create(ToolDefinition definition, long durationMillis, RuntimeClockService runtimeClockService, long warningMillis) {
      ItemStack item = new ItemStack(definition.material());
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return item;
      } else {
         meta.setDisplayName(ColorUtil.color(definition.name()));
         meta.setLore(buildLore(definition.lore(), durationMillis, warningMillis));
         definition.enchants().forEach((enchant, level) -> meta.addEnchant(enchant, level, true));

         try {
            meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
         } catch (Throwable var9) {
         }

         item.setItemMeta(meta);
         String uid = UUID.randomUUID().toString();
         NBT.modify(item, (nbt) -> {
            nbt.setString("tktool-id", definition.id());
            nbt.setLong("tktool-duration-ms", durationMillis);
            nbt.setLong("tktool-remaining-ms", durationMillis);
            nbt.setString("tktool-uid", uid);
         });
         return item;
      }
   }

   public static boolean refreshTimedLore(ItemStack item, ToolDefinition definition, long remainingMillis, long warningMillis) {
      if (item != null && item.getType() != Material.AIR && definition != null) {
         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            return false;
         } else {
            String desiredName = ColorUtil.color(definition.name());
            List<String> desiredLore = buildLore(definition.lore(), remainingMillis, warningMillis);
            List<String> extraLore = extractExtraLore(meta.getLore(), definition);
            if (!extraLore.isEmpty()) {
               desiredLore.addAll(extraLore);
            }

            boolean changed = !desiredName.equals(meta.getDisplayName()) || !desiredLore.equals(meta.getLore());
            meta.setDisplayName(desiredName);
            meta.setLore(desiredLore);

            try {
               if (!meta.getItemFlags().contains(ItemFlag.HIDE_ATTRIBUTES)) {
                  meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
                  changed = true;
               }
            } catch (Throwable var12) {
            }

            if (!changed) {
               return false;
            } else {
               item.setItemMeta(meta);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private static List<String> buildLore(List<String> baseLore, long remainingMillis, long warningMillis) {
      List<String> lore = new ArrayList();

      for(String line : baseLore) {
         lore.add(ColorUtil.color(line));
      }

      appendTimerLore(lore, remainingMillis, warningMillis);
      return lore;
   }

   private static void appendTimerLore(List<String> lore, long remainingMillis, long warningMillis) {
      String color = remainingMillis < 259200000L ? "&c" : "&8";
      lore.add(ColorUtil.color(color + "Self Destruct:"));
      lore.add(ColorUtil.color(color + DurationFormatter.formatSelfDestruct(remainingMillis)));
   }

   private static List<String> extractExtraLore(List<String> currentLore, ToolDefinition definition) {
      if (currentLore != null && !currentLore.isEmpty()) {
         List<String> baseLore = new ArrayList(definition.lore().size());

         for(String line : definition.lore()) {
            baseLore.add(ColorUtil.color(line));
         }

         int minimumToolLoreLines = baseLore.size() + 2;
         if (currentLore.size() < minimumToolLoreLines) {
            return List.of();
         } else {
            for(int i = 0; i < baseLore.size(); ++i) {
               if (!((String)baseLore.get(i)).equals(currentLore.get(i))) {
                  return List.of();
               }
            }

            if (!isTimerLabel((String)currentLore.get(baseLore.size()))) {
               return List.of();
            } else {
               return new ArrayList(currentLore.subList(minimumToolLoreLines, currentLore.size()));
            }
         }
      } else {
         return List.of();
      }
   }

   private static boolean isTimerLabel(String line) {
      if (line == null) {
         return false;
      } else {
         String normalized = line.replace("§", "&").toLowerCase();
         return normalized.endsWith("self destruct:");
      }
   }
}
