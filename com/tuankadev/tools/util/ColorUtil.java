package com.tuankadev.tools.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;

public final class ColorUtil {
   private static final Pattern HEX_PATTERN = Pattern.compile("&?#([A-Fa-f0-9]{6})");

   private ColorUtil() {
   }

   public static String color(String input) {
      if (input == null) {
         return "";
      } else {
         Matcher matcher = HEX_PATTERN.matcher(input);
         StringBuilder builder = new StringBuilder();

         while(matcher.find()) {
            matcher.appendReplacement(builder, ChatColor.of("#" + matcher.group(1)).toString());
         }

         matcher.appendTail(builder);
         return ChatColor.translateAlternateColorCodes('&', builder.toString());
      }
   }
}
