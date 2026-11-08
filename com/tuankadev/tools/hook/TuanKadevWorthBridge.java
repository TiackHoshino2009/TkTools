package com.tuankadev.tools.hook;

import com.tuankadev.tools.TktoolsUpdate;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class TuanKadevWorthBridge {
   private final TktoolsUpdate plugin;
   private Plugin cachedWorthPlugin;

   public TuanKadevWorthBridge(TktoolsUpdate plugin) {
      this.plugin = plugin;
   }

   public boolean isHooked() {
      Plugin worth = this.getWorthPlugin();
      return worth != null && worth.isEnabled();
   }

   public boolean sellContainer(Player player, Inventory inventory, String sourceName) {
      Plugin worth = this.getWorthPlugin();
      if (worth != null && worth.isEnabled()) {
         try {
            Method method = worth.getClass().getMethod("sellContainerContents", Player.class, Inventory.class, String.class);
            method.invoke(worth, player, inventory, sourceName);
            return true;
         } catch (Throwable throwable) {
            this.plugin.getLogger().warning("Could not call TuanKadevWorth hook: " + throwable.getMessage());
            return false;
         }
      } else {
         return false;
      }
   }

   private Plugin getWorthPlugin() {
      if (this.cachedWorthPlugin != null && this.cachedWorthPlugin.isEnabled()) {
         return this.cachedWorthPlugin;
      } else {
         this.cachedWorthPlugin = Bukkit.getPluginManager().getPlugin("TuanKadevWorth");
         return this.cachedWorthPlugin;
      }
   }
}
