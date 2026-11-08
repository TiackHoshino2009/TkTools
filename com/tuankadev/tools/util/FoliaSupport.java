package com.tuankadev.tools.util;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class FoliaSupport {
   private static final boolean FOLIA = isClassPresent("io.papermc.paper.threadedregions.RegionizedServer");

   private FoliaSupport() {
   }

   public static boolean isFolia() {
      return FOLIA;
   }

   public static ScheduledHandle runGlobalRepeating(Plugin plugin, long delayTicks, long periodTicks, Runnable runnable) {
      if (!FOLIA) {
         BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
         Objects.requireNonNull(task);
         return task::cancel;
      } else {
         try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method runAtFixedRate = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, Long.TYPE, Long.TYPE);
            Object task = runAtFixedRate.invoke(scheduler, plugin, (Consumer)(ignored) -> runnable.run(), delayTicks, periodTicks);
            return () -> invokeCancel(task);
         } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to schedule Folia global task", exception);
         }
      }
   }

   public static void runForPlayer(Plugin plugin, Entity entity, Runnable runnable) {
      runNextTick(plugin, entity, runnable);
   }

   public static void runNextTick(Plugin plugin, Entity entity, Runnable runnable) {
      if (FOLIA) {
         try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method run = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            run.invoke(scheduler, plugin, (Consumer)(ignored) -> runnable.run(), null);
         } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to schedule Folia entity task", exception);
         }
      } else {
         Bukkit.getScheduler().runTask(plugin, runnable);
      }

   }

   public static void runAtLocation(Plugin plugin, Location location, Runnable runnable) {
      if (!FOLIA) {
         Bukkit.getScheduler().runTask(plugin, runnable);
      } else {
         try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            Method run = scheduler.getClass().getMethod("run", Plugin.class, Location.class, Consumer.class);
            run.invoke(scheduler, plugin, location, (Consumer)(ignored) -> runnable.run());
         } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to schedule Folia region task", exception);
         }
      }

   }

   private static void invokeCancel(Object task) {
      if (task != null) {
         try {
            Method cancel = task.getClass().getMethod("cancel");
            cancel.invoke(task);
         } catch (ReflectiveOperationException var2) {
         }
      }

   }

   private static boolean isClassPresent(String name) {
      try {
         Class.forName(name);
         return true;
      } catch (ClassNotFoundException var2) {
         return false;
      }
   }

   @FunctionalInterface
   public interface ScheduledHandle {
      void cancel();
   }
}
