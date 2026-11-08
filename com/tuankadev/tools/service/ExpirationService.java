package com.tuankadev.tools.service;

import com.tuankadev.tools.TktoolsUpdate;
import com.tuankadev.tools.tool.ToolDefinition;
import com.tuankadev.tools.tool.ToolFactory;
import com.tuankadev.tools.util.FoliaSupport;
import de.tr7zw.nbtapi.NBT;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

public final class ExpirationService {
   public static final String NBT_TOOL_ID = "tktool-id";
   public static final String NBT_REMAINING = "tktool-remaining-ms";
   public static final String NBT_UID = "tktool-uid";
   public static final String NBT_DURATION = "tktool-duration-ms";
   public static final String NBT_EXPIRES_AT = "tktool-expire-runtime-ms";
   private static final long STEP_MS = 60000L;
   private final TktoolsUpdate plugin;
   private final RuntimeClockService runtimeClockService;
   private final NamespacedKey carryKey;
   private final Map<UUID, Long> carry = new ConcurrentHashMap();
   private volatile long lastTickNano;
   private FoliaSupport.ScheduledHandle scanTask;

   public ExpirationService(TktoolsUpdate plugin, RuntimeClockService runtimeClockService) {
      this.plugin = plugin;
      this.runtimeClockService = runtimeClockService;
      this.carryKey = new NamespacedKey(plugin, "carry-ms");
   }

   public void start() {
      this.stop();
      this.runtimeClockService.startPersistTask();
      long ticks = Math.max(1L, this.plugin.getConfig().getLong("settings.countdown-ticks", 20L));
      this.lastTickNano = System.nanoTime();
      this.scanTask = FoliaSupport.runGlobalRepeating(this.plugin, ticks, ticks, this::tick);
   }

   public void stop() {
      if (this.scanTask != null) {
         this.scanTask.cancel();
         this.scanTask = null;
      }

      this.runtimeClockService.stopPersistTask();
   }

   /** @deprecated */
   @Deprecated(
      forRemoval = false
   )
   public void reload() {
      this.start();
      this.scanAllAccessibleInventories();
   }

   private void tick() {
      long now = System.nanoTime();
      long delta = Math.min(Math.max(0L, (now - this.lastTickNano) / 1000000L), 60000L);
      this.lastTickNano = now;

      for(Player player : Bukkit.getOnlinePlayers()) {
         FoliaSupport.runForPlayer(this.plugin, player, () -> this.advance(player, delta));
      }

   }

   private void advance(Player player, long delta) {
      UUID id = player.getUniqueId();
      long total = (Long)this.carry.computeIfAbsent(id, (key) -> this.loadCarry(player)) + delta;
      long steps = total / 60000L;
      this.carry.put(id, total % 60000L);
      if (steps > 0L) {
         this.saveCarry(player);
         this.scan(player, player.getInventory(), true, true, steps * 60000L, true);
      }

   }

   private long loadCarry(Player player) {
      Long value = (Long)player.getPersistentDataContainer().get(this.carryKey, PersistentDataType.LONG);
      return value == null ? 0L : Math.max(0L, Math.min(value, 59999L));
   }

   private void saveCarry(Player player) {
      Long value = (Long)this.carry.get(player.getUniqueId());
      if (value != null) {
         player.getPersistentDataContainer().set(this.carryKey, PersistentDataType.LONG, value);
      }

   }

   public void flushPlayer(Player player) {
      this.saveCarry(player);
      this.carry.remove(player.getUniqueId());
   }

   public void flushAll() {
      for(Player player : Bukkit.getOnlinePlayers()) {
         try {
            this.saveCarry(player);
         } catch (Throwable var4) {
         }
      }

   }

   public long getRemainingMillis(ItemStack itemStack) {
      if (itemStack != null && itemStack.getType() != Material.AIR) {
         Long value = (Long)NBT.get(itemStack, (nbt) -> {
            if (nbt.hasTag("tktool-remaining-ms")) {
               return nbt.getLong("tktool-remaining-ms");
            } else {
               return nbt.hasTag("tktool-expire-runtime-ms") ? nbt.getLong("tktool-expire-runtime-ms") - this.runtimeClockService.getRuntimeMillis() : -1L;
            }
         });
         return value == null ? -1L : value;
      } else {
         return -1L;
      }
   }

   public boolean isExpired(ItemStack itemStack) {
      return !this.hasTimer(itemStack) ? false : this.getRemainingMillis(itemStack) <= 0L;
   }

   public boolean hasTimer(ItemStack itemStack) {
      if (itemStack != null && itemStack.getType() != Material.AIR) {
         Boolean result = (Boolean)NBT.get(itemStack, (nbt) -> nbt.hasTag("tktool-remaining-ms") || nbt.hasTag("tktool-expire-runtime-ms"));
         return result != null && result;
      } else {
         return false;
      }
   }

   public void scanOnlinePlayers() {
      for(Player player : Bukkit.getOnlinePlayers()) {
         FoliaSupport.runForPlayer(this.plugin, player, () -> this.scan(player, player.getInventory(), false, true, 0L, false));
      }

   }

   public void scanAllAccessibleInventories() {
      this.scanOnlinePlayers();
   }

   public void refreshLoreOnlinePlayers() {
      this.scanOnlinePlayers();
   }

   public void scanContainerState(BlockState state) {
   }

   public void scanInventory(Player owner, Inventory inventory, boolean notify, boolean refreshLore) {
      if (inventory instanceof PlayerInventory) {
         this.scan(owner, inventory, notify, refreshLore, 0L, false);
      }

   }

   private static long minutes(long millis) {
      return (millis + 59999L) / 60000L;
   }

   private boolean migrateLegacy(ItemStack item) {
      Boolean migrated = (Boolean)NBT.modify(item, (nbt) -> {
         if (!nbt.hasTag("tktool-expire-runtime-ms")) {
            return false;
         } else {
            long remaining = nbt.getLong("tktool-expire-runtime-ms") - this.runtimeClockService.getRuntimeMillis();
            nbt.setLong("tktool-remaining-ms", Math.max(0L, remaining));
            if (!nbt.hasTag("tktool-uid")) {
               nbt.setString("tktool-uid", UUID.randomUUID().toString());
            }

            nbt.removeKey("tktool-expire-runtime-ms");
            return true;
         }
      });
      return migrated != null && migrated;
   }

   private TimerData read(ItemStack item) {
      return (TimerData)NBT.get(item, (nbt) -> nbt.hasTag("tktool-remaining-ms") ? new TimerData((String)nbt.getOrNull("tktool-id", String.class), nbt.getLong("tktool-remaining-ms"), (String)nbt.getOrNull("tktool-uid", String.class)) : null);
   }

   private void scan(Player owner, Inventory inventory, boolean notify, boolean refreshLore, long subtract, boolean countdown) {
      ItemStack[] contents = inventory.getContents();
      boolean changed = false;
      boolean playerInventory = inventory instanceof PlayerInventory;
      Set<String> seen = playerInventory ? new HashSet() : null;
      long warningMillis = this.plugin.getConfig().getLong("settings.timer-warning-ms", 86400000L);

      for(int slot = 0; slot < contents.length; ++slot) {
         ItemStack item = contents[slot];
         if (item != null && item.getType() != Material.AIR && item.hasItemMeta() && this.hasTimer(item)) {
            if (this.migrateLegacy(item)) {
               contents[slot] = item;
               changed = true;
            }

            TimerData data = this.read(item);
            if (data != null) {
               long remaining = data.stored();
               if (countdown) {
                  remaining -= subtract;
               }

               if (countdown && remaining <= 0L) {
                  contents[slot] = null;
                  changed = true;
                  if (notify && owner != null) {
                     owner.playSound(owner.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
                  }
               } else {
                  boolean wrote = false;
                  if (remaining != data.stored()) {
                     NBT.modify(item, (nbt) -> nbt.setLong("tktool-remaining-ms", remaining));
                     wrote = true;
                  }

                  if (seen != null) {
                     String uid = data.uid();
                     if (uid == null || !seen.add(uid)) {
                        String fresh = UUID.randomUUID().toString();
                        NBT.modify(item, (nbt) -> nbt.setString("tktool-uid", fresh));
                        seen.add(fresh);
                        wrote = true;
                     }
                  }

                  if (refreshLore || wrote) {
                     ToolDefinition definition = data.toolId() == null ? null : this.plugin.toolConfigService().getTool(data.toolId());
                     if (ToolFactory.refreshTimedLore(item, definition, minutes(remaining) * 60000L, warningMillis)) {
                        wrote = true;
                     }
                  }

                  if (wrote) {
                     contents[slot] = item;
                     changed = true;
                  }
               }
            }
         }
      }

      if (changed) {
         inventory.setContents(contents);
      }

   }

   private static record TimerData(String toolId, long stored, String uid) {
   }
}
