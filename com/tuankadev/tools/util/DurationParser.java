package com.tuankadev.tools.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
   private static final Pattern PART = Pattern.compile("(\\d+)([smhd])", 2);

   private DurationParser() {
   }

   public static long parseMillis(String input) {
      if (input != null && !input.isBlank()) {
         String normalized = input.replaceAll("\\s+", "");
         Matcher matcher = PART.matcher(normalized);
         long total = 0L;

         int consumed;
         for(consumed = 0; matcher.find(); consumed += matcher.group(0).length()) {
            long value = Long.parseLong(matcher.group(1));
            switch (matcher.group(2).toLowerCase()) {
               case "s":
                  total += value * 1000L;
                  break;
               case "m":
                  total += value * 60000L;
                  break;
               case "h":
                  total += value * 3600000L;
                  break;
               case "d":
                  total += value * 86400000L;
            }
         }

         return consumed == normalized.length() && total > 0L ? total : -1L;
      } else {
         return -1L;
      }
   }
}
