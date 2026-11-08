package com.tuankadev.tools.service;

import com.tuankadev.tools.TktoolsUpdate;
import com.tuankadev.tools.util.FoliaSupport;

public final class RuntimeClockService {
   private final TktoolsUpdate plugin;
   private long bootNano;
   private long baseRuntimeMillis;
   private FoliaSupport.ScheduledHandle persistTask;

   public RuntimeClockService(TktoolsUpdate plugin) {
      this.plugin = plugin;
   }

   public void start() {
      this.baseRuntimeMillis = this.plugin.getConfig().getLong("runtime.elapsed-ms", 0L);
      this.bootNano = System.nanoTime();
   }

   public void stop() {
      this.stopPersistTask();
      this.persistNow();
   }

   public void reload() {
      long persistedRuntimeMillis = this.plugin.getConfig().getLong("runtime.elapsed-ms", 0L);
      long currentRuntimeMillis = Math.max(this.baseRuntimeMillis, this.getRuntimeMillis());
      this.baseRuntimeMillis = Math.max(currentRuntimeMillis, persistedRuntimeMillis);
      this.bootNano = System.nanoTime();
   }

   public long getRuntimeMillis() {
      long live = (System.nanoTime() - this.bootNano) / 1000000L;
      return this.baseRuntimeMillis + Math.max(0L, live);
   }

   public void startPersistTask() {
      this.stopPersistTask();
      this.persistTask = FoliaSupport.runGlobalRepeating(this.plugin, 1200L, 1200L, this::persistNow);
   }

   public void stopPersistTask() {
      if (this.persistTask != null) {
         this.persistTask.cancel();
         this.persistTask = null;
      }

   }

   public void persistNow() {
      long current = this.getRuntimeMillis();
      this.baseRuntimeMillis = current;
      this.bootNano = System.nanoTime();
      this.plugin.getConfig().set("runtime.elapsed-ms", current);
      this.plugin.saveConfig();
   }
}
