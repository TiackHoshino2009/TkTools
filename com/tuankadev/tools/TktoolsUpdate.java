package com.tuankadev.tools;

import com.tuankadev.tools.command.ToolsCommand;
import com.tuankadev.tools.config.MessageService;
import com.tuankadev.tools.config.ToolConfigService;
import com.tuankadev.tools.hook.TuanKadevWorthBridge;
import com.tuankadev.tools.listener.ToolListener;
import com.tuankadev.tools.service.ExpirationService;
import com.tuankadev.tools.service.RuntimeClockService;
import java.io.File;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class TktoolsUpdate extends JavaPlugin {
   private YamlConfiguration messageConfig;
   private YamlConfiguration toolsConfig;
   private MessageService messages;
   private ToolConfigService toolConfigService;
   private RuntimeClockService runtimeClockService;
   private ExpirationService expirationService;
   private ToolListener toolListener;
   private TuanKadevWorthBridge tuankadevWorthBridge;
   private ToolsCommand toolsCommand;

   public void onEnable() {
      this.saveDefaultConfig();
      this.saveResourceIfMissing("message.yml");
      this.saveResourceIfMissing("tools.yml");
      this.loadExtraConfigs();
      this.messages = new MessageService(this, this.messageConfig);
      this.toolConfigService = new ToolConfigService(this, this.toolsConfig);
      this.runtimeClockService = new RuntimeClockService(this);
      this.expirationService = new ExpirationService(this, this.runtimeClockService);
      this.tuankadevWorthBridge = new TuanKadevWorthBridge(this);
      this.runtimeClockService.start();
      this.expirationService.start();
      this.toolListener = new ToolListener(this, this.runtimeClockService, this.expirationService);
      this.getServer().getPluginManager().registerEvents(this.toolListener, this);
      this.registerCommandBindings();
   }

   public void onDisable() {
      if (this.toolListener != null) {
         this.toolListener.stopLoreRefreshTask();
         HandlerList.unregisterAll(this.toolListener);
      }

      if (this.expirationService != null) {
         this.expirationService.flushAll();
         this.expirationService.stop();
      }

      if (this.runtimeClockService != null) {
         this.runtimeClockService.stop();
      }

   }

   public void reloadAll() {
      this.reloadConfig();
      this.loadExtraConfigs();
      if (this.toolListener != null) {
         this.toolListener.stopLoreRefreshTask();
         HandlerList.unregisterAll(this.toolListener);
      }

      if (this.expirationService != null) {
         this.expirationService.stop();
      }

      this.messages = new MessageService(this, this.messageConfig);
      this.toolConfigService = new ToolConfigService(this, this.toolsConfig);
      this.runtimeClockService.reload();
      this.expirationService.start();
      this.toolListener = new ToolListener(this, this.runtimeClockService, this.expirationService);
      this.getServer().getPluginManager().registerEvents(this.toolListener, this);
      this.registerCommandBindings();
      this.expirationService.scanAllAccessibleInventories();
   }

   private void registerCommandBindings() {
      if (this.toolsCommand == null) {
         this.toolsCommand = new ToolsCommand(this, this.toolConfigService, this.runtimeClockService, this.expirationService, this.messages);
      } else {
         this.toolsCommand.reloadDependencies(this.toolConfigService, this.runtimeClockService, this.expirationService, this.messages);
      }

      PluginCommand command = this.getCommand("tools");
      if (command != null) {
         command.setExecutor(this.toolsCommand);
         command.setTabCompleter(this.toolsCommand);
      }

   }

   private void loadExtraConfigs() {
      this.messageConfig = YamlConfiguration.loadConfiguration(new File(this.getDataFolder(), "message.yml"));
      this.toolsConfig = YamlConfiguration.loadConfiguration(new File(this.getDataFolder(), "tools.yml"));
   }

   private void saveResourceIfMissing(String path) {
      File file = new File(this.getDataFolder(), path);
      if (!file.exists()) {
         this.saveResource(path, false);
      }

   }

   public MessageService messages() {
      return this.messages;
   }

   public ToolConfigService toolConfigService() {
      return this.toolConfigService;
   }

   public TuanKadevWorthBridge tuankadevWorthBridge() {
      return this.tuankadevWorthBridge;
   }
}
