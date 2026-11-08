package com.tuankadev.tools.config;

import com.tuankadev.tools.TktoolsUpdate;
import com.tuankadev.tools.util.ColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public final class MessageService {
   private final FileConfiguration config;

   public MessageService(TktoolsUpdate plugin, FileConfiguration config) {
      this.config = config;
   }

   public String get(String path) {
      return ColorUtil.color(this.config.getString(path, path));
   }

   public String prefixed(String path) {
      String var10000 = this.get("prefix");
      return var10000 + this.get(path);
   }

   public void send(CommandSender sender, String path) {
      sender.sendMessage(this.prefixed(path));
   }

   public void send(CommandSender sender, String path, String... replacements) {
      String msg = this.prefixed(path);

      for(int i = 0; i + 1 < replacements.length; i += 2) {
         msg = msg.replace(replacements[i], replacements[i + 1]);
      }

      sender.sendMessage(msg);
   }
}
