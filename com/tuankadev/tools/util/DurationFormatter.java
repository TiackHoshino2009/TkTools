package com.tuankadev.tools.util;

import java.util.concurrent.TimeUnit;

public final class DurationFormatter {
   private DurationFormatter() {
   }

   public static String formatSelfDestruct(long millis) {
      long totalSeconds = Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(millis));
      long totalMinutes = totalSeconds / 60L;
      long days = totalMinutes / 1440L;
      long hours = totalMinutes % 1440L / 60L;
      long minutes = totalMinutes % 60L;
      long seconds = totalSeconds % 60L;
      if (totalSeconds < 60L) {
         return seconds + "s";
      } else {
         StringBuilder out = new StringBuilder();
         if (days > 0L) {
            out.append(days).append("d");
         }

         if (hours > 0L) {
            if (!out.isEmpty()) {
               out.append(' ');
            }

            out.append(hours).append("h");
         }

         if (minutes > 0L || out.isEmpty()) {
            if (!out.isEmpty()) {
               out.append(' ');
            }

            out.append(minutes).append("m");
         }

         return out.toString();
      }
   }
}
