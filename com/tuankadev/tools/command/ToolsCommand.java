package com.tuankadev.tools.command;

import com.tuankadev.tools.TktoolsUpdate;
import com.tuankadev.tools.config.MessageService;
import com.tuankadev.tools.config.ToolConfigService;
import com.tuankadev.tools.service.ExpirationService;
import com.tuankadev.tools.service.RuntimeClockService;
import com.tuankadev.tools.tool.ToolDefinition;
import com.tuankadev.tools.tool.ToolFactory;
import com.tuankadev.tools.util.DurationParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ToolsCommand implements CommandExecutor, TabCompleter {
   private final TktoolsUpdate plugin;
   private ToolConfigService toolConfigService;
   private RuntimeClockService runtimeClockService;
   private ExpirationService expirationService;
   private MessageService messages;

   public ToolsCommand(TktoolsUpdate plugin, ToolConfigService toolConfigService, RuntimeClockService runtimeClockService, ExpirationService expirationService, MessageService messages) {
      this.plugin = plugin;
      this.reloadDependencies(toolConfigService, runtimeClockService, expirationService, messages);
   }

   public void reloadDependencies(ToolConfigService toolConfigService, RuntimeClockService runtimeClockService, ExpirationService expirationService, MessageService messages) {
      this.toolConfigService = toolConfigService;
      this.runtimeClockService = runtimeClockService;
      this.expirationService = expirationService;
      this.messages = messages;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (args.length == 0) {
         return false;
      } else if (args[0].equalsIgnoreCase("reload")) {
         if (!sender.hasPermission("tktool.command.reload")) {
            this.messages.send(sender, "no-permission");
            return true;
         } else {
            this.plugin.reloadAll();
            this.plugin.messages().send(sender, "reload-success");
            return true;
         }
      } else if (args[0].equalsIgnoreCase("give")) {
         if (!sender.hasPermission("tktool.command.give")) {
            this.messages.send(sender, "no-permission");
            return true;
         } else if (args.length < 4) {
            return false;
         } else {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
               this.messages.send(sender, "player-not-found", "%player%", args[1]);
               return true;
            } else {
               ToolDefinition definition = this.toolConfigService.getTool(args[2]);
               if (definition == null) {
                  this.messages.send(sender, "invalid-tool", "%tool%", args[2]);
                  return true;
               } else {
                  long duration = DurationParser.parseMillis(args[3].toLowerCase(Locale.ROOT));
                  if (duration <= 0L) {
                     this.messages.send(sender, "invalid-duration", "%input%", args[3]);
                     return true;
                  } else {
                     long warningMillis = this.plugin.getConfig().getLong("settings.timer-warning-ms", 86400000L);
                     ItemStack item = ToolFactory.create(definition, duration, this.runtimeClockService, warningMillis);
                     Map<Integer, ItemStack> leftovers = target.getInventory().addItem(new ItemStack[]{item});
                     if (!leftovers.isEmpty()) {
                        String var10001 = this.messages.get("prefix");
                        sender.sendMessage(var10001 + "Inventory of " + target.getName() + " is full, dropped " + definition.id() + " at their location.");
                        leftovers.values().forEach((leftover) -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
                     }

                     this.expirationService.scanInventory(target, target.getInventory(), false, true);
                     this.messages.send(sender, "give-success", "%tool%", definition.id(), "%player%", target.getName(), "%duration%", args[3]);
                     return true;
                  }
               }
            }
         }
      } else {
         return false;
      }
   }

   public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
      List<String> result = new ArrayList();
      if (args.length == 1) {
         if (sender.hasPermission("tktool.command.give")) {
            result.add("give");
         }

         if (sender.hasPermission("tktool.command.reload")) {
            result.add("reload");
         }

         return this.filter(result, args[0]);
      } else if (args[0].equalsIgnoreCase("give") && sender.hasPermission("tktool.command.give")) {
         if (args.length != 2) {
            if (args.length == 3) {
               result.addAll(this.toolConfigService.getToolIds());
               return this.filter(result, args[2]);
            } else if (args.length == 4) {
               result.addAll(List.of("1s", "30s", "1m", "5m", "1h", "1d", "7d", "30d"));
               return this.filter(result, args[3]);
            } else {
               return List.of();
            }
         } else {
            for(Player player : Bukkit.getOnlinePlayers()) {
               result.add(player.getName());
            }

            return this.filter(result, args[1]);
         }
      } else {
         return List.of();
      }
   }

   private List<String> filter(List<String> input, String current) {
      String lower = current.toLowerCase(Locale.ROOT);
      return input.stream().filter((entry) -> entry.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
   }
}
