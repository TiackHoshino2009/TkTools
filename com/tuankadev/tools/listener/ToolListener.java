package com.tuankadev.tools.listener;

import com.tuankadev.tools.TktoolsUpdate;
import com.tuankadev.tools.service.ExpirationService;
import com.tuankadev.tools.service.RuntimeClockService;
import com.tuankadev.tools.util.FoliaSupport;
import de.tr7zw.nbtapi.NBT;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class ToolListener implements Listener {
   private final Set<Material> blacklist;
   private final Set<String> blacklistedWorlds;
   private static final BlockFace[] CARDINALS;
   private static final int[][] LOG_NEIGHBORS;
   private static final int[][] VEIN_NEIGHBORS;
   private final TktoolsUpdate plugin;
   private final ExpirationService expirationService;
   private FoliaSupport.ScheduledHandle loreRefreshTask;

   public ToolListener(TktoolsUpdate plugin, RuntimeClockService runtimeClockService, ExpirationService expirationService) {
      this.plugin = plugin;
      this.expirationService = expirationService;
      this.blacklist = this.loadBlacklist(plugin);
      this.blacklistedWorlds = this.loadBlacklistedWorlds(plugin);
      this.restartLoreRefreshTask();
   }

   public void restartLoreRefreshTask() {
      this.stopLoreRefreshTask();
      long ticks = this.plugin.getConfig().getLong("settings.timer-refresh-ticks", 6000L);
      if (ticks > 0L) {
         TktoolsUpdate var10001 = this.plugin;
         ExpirationService var10004 = this.expirationService;
         Objects.requireNonNull(var10004);
         this.loreRefreshTask = FoliaSupport.runGlobalRepeating(var10001, ticks, ticks, var10004::refreshLoreOnlinePlayers);
      }

   }

   public void stopLoreRefreshTask() {
      if (this.loreRefreshTask != null) {
         this.loreRefreshTask.cancel();
         this.loreRefreshTask = null;
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      ItemStack tool = player.getInventory().getItemInMainHand();
      int toolSlot = player.getInventory().getHeldItemSlot();
      String toolId = this.getToolId(tool);
      if (toolId != null) {
         if (this.expirationService.isExpired(tool)) {
            this.breakExpiredMainHandTool(player);
            event.setCancelled(true);
         } else if (toolId.equals("drill")) {
            event.setCancelled(true);
            FoliaSupport.runAtLocation(this.plugin, event.getBlock().getLocation(), () -> this.runDrill(player, event.getBlock(), tool, toolSlot));
         } else if (!this.isWorldBlacklisted(event.getBlock().getWorld())) {
            if (toolId.equals("veinminer")) {
               if ((!this.plugin.getConfig().getBoolean("settings.veinminer-requires-sneak", true) || player.isSneaking()) && this.isVeinMineable(event.getBlock().getType()) && (!this.plugin.getConfig().getBoolean("settings.veinminer-need-correct-tool", true) || event.getBlock().isPreferredTool(tool))) {
                  event.setCancelled(true);
                  this.runVeinMiner(player, event.getBlock(), tool, toolSlot);
               }
            } else if (toolId.equals("treechopper")) {
               event.setCancelled(true);
               FoliaSupport.runAtLocation(this.plugin, event.getBlock().getLocation(), () -> this.runTreeChopper(player, event.getBlock(), tool, toolSlot));
            } else if (toolId.equals("sellaxe")) {
               event.setCancelled(true);
               FoliaSupport.runAtLocation(this.plugin, event.getBlock().getLocation(), () -> this.handleSellAxe(player, event.getBlock()));
            }
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onInteract(PlayerInteractEvent event) {
      if (event.getHand() == EquipmentSlot.HAND) {
         ItemStack item = event.getItem();
         String toolId = this.getToolId(item);
         if (toolId != null) {
            Player player = event.getPlayer();
            if (this.expirationService.isExpired(item)) {
               this.breakExpiredMainHandTool(player);
               event.setCancelled(true);
            } else if (toolId.equals("sellaxe")) {
               if (!this.isWorldBlacklisted(player.getWorld()) && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
                  event.setCancelled(true);
                  event.setUseInteractedBlock(Result.DENY);
                  event.setUseItemInHand(Result.DENY);
                  Block clicked = event.getClickedBlock();
                  if (clicked != null) {
                     FoliaSupport.runAtLocation(this.plugin, clicked.getLocation(), () -> this.handleSellAxe(player, clicked));
                  }
               }
            } else if (!toolId.equals("bucket") || event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
               if (toolId.equals("rocket") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                  int slot = player.getInventory().getHeldItemSlot();
                  ItemStack snapshot = item.clone();
                  this.restoreSlotLater(player, slot, snapshot, 3);
               }
            } else if (!this.isWorldBlacklisted(player.getWorld())) {
               Block target = this.resolveBucketTarget(event);
               if (target != null && this.isWater(target)) {
                  ItemStack originalBucket = item.clone();
                  event.setCancelled(true);
                  event.setUseInteractedBlock(Result.DENY);
                  event.setUseItemInHand(Result.DENY);
                  FoliaSupport.runAtLocation(this.plugin, target.getLocation(), () -> {
                     this.drainWater(player, target, 27);
                     this.restoreBucket(player, event.getHand(), originalBucket);
                  });
               }
            }
         }
      }

   }

   private void handleSellAxe(Player player, Block clicked) {
      Inventory inventory = null;
      String sourceName = null;
      BlockState state = clicked.getState();
      if (state instanceof Chest chest) {
         inventory = chest.getInventory();
         sourceName = inventory instanceof DoubleChestInventory ? "Large Chest" : "Chest";
      } else if (state instanceof Barrel barrel) {
         inventory = barrel.getInventory();
         sourceName = "Barrel";
      } else if (state instanceof ShulkerBox shulkerBox) {
         inventory = shulkerBox.getInventory();
         sourceName = "Shulker Box";
      }

      if (inventory != null) {
         if (!this.plugin.tuankadevWorthBridge().isHooked()) {
            this.plugin.getLogger().warning("TuanKadevWorth is not hooked, sell axe action skipped.");
         } else {
            this.plugin.tuankadevWorthBridge().sellContainer(player, inventory, sourceName);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onBucketFill(PlayerBucketFillEvent event) {
      ItemStack original = event.getItemStack();
      if (!"bucket".equalsIgnoreCase(this.getToolId(original))) {
         ItemStack handItem = event.getPlayer().getInventory().getItem(event.getHand());
         if (!"bucket".equalsIgnoreCase(this.getToolId(handItem))) {
            return;
         }

         original = handItem.clone();
      }

      event.setCancelled(true);
      this.restoreBucket(event.getPlayer(), event.getHand(), original);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onBucketEmpty(PlayerBucketEmptyEvent event) {
      ItemStack original = event.getPlayer().getInventory().getItem(event.getHand());
      if ("bucket".equalsIgnoreCase(this.getToolId(original))) {
         event.setCancelled(true);
         this.restoreBucket(event.getPlayer(), event.getHand(), original.clone());
      }

   }

   private void restoreBucket(Player player, EquipmentSlot hand, ItemStack original) {
      if ("bucket".equalsIgnoreCase(this.getToolId(original))) {
         ItemStack restored = original.clone();
         restored.setType(Material.BUCKET);
         restored.setAmount(1);
         int heldSlot = player.getInventory().getHeldItemSlot();
         FoliaSupport.runNextTick(this.plugin, player, () -> {
            if (hand == EquipmentSlot.OFF_HAND) {
               player.getInventory().setItemInOffHand(restored);
            } else {
               ItemStack currentItem = player.getInventory().getItem(heldSlot);
               if (currentItem == null || currentItem.getType() == Material.AIR || "bucket".equalsIgnoreCase(this.getToolId(currentItem))) {
                  player.getInventory().setItem(heldSlot, restored);
               }
            }

            player.updateInventory();
            this.expirationService.scanInventory(player, player.getInventory(), false, true);
         });
      }

   }

   private void restoreSlotLater(Player player, int slot, ItemStack snapshot, int repeats) {
      if (repeats <= 0) {
         this.expirationService.scanInventory(player, player.getInventory(), false, true);
      } else {
         FoliaSupport.runNextTick(this.plugin, player, () -> {
            ItemStack currentItem = player.getInventory().getItem(slot);
            if (currentItem == null || currentItem.getType() == Material.AIR || this.sameTool(currentItem, snapshot)) {
               player.getInventory().setItem(slot, snapshot.clone());
            }

            this.restoreSlotLater(player, slot, snapshot, repeats - 1);
         });
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onSellAxeEntityInteract(PlayerInteractEntityEvent event) {
      ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
      if ("sellaxe".equalsIgnoreCase(this.getToolId(hand))) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onSellAxeDamage(EntityDamageByEntityEvent event) {
      Entity var3 = event.getDamager();
      if (var3 instanceof Player player) {
         if ("sellaxe".equalsIgnoreCase(this.getToolId(player.getInventory().getItemInMainHand()))) {
            event.setCancelled(true);
         }
      }

   }

   @EventHandler
   public void onHeld(PlayerItemHeldEvent event) {
      ItemStack next = event.getPlayer().getInventory().getItem(event.getNewSlot());
      if (this.getToolId(next) != null) {
         Sound sound = this.resolveConfiguredSound("settings.held-sound", "BLOCK_AMETHYST_BLOCK_RESONATE");
         event.getPlayer().playSound(event.getPlayer().getLocation(), sound, 1.0F, 1.0F);
      }

   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.expirationService.scanInventory(event.getPlayer(), event.getPlayer().getInventory(), false, true);
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.expirationService.flushPlayer(event.getPlayer());
   }

   private boolean isTimedTool(ItemStack item) {
      return item != null && item.getType() != Material.AIR && (this.getToolId(item) != null || this.expirationService.hasTimer(item));
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onAnvilPrepare(PrepareAnvilEvent event) {
      ItemStack first = event.getInventory().getItem(0);
      ItemStack second = event.getInventory().getItem(1);
      if (this.isTimedTool(second) || this.isTimedTool(first) && second != null && second.getType() == first.getType()) {
         event.setResult((ItemStack)null);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onCraftPrepare(PrepareItemCraftEvent event) {
      for(ItemStack ingredient : event.getInventory().getMatrix()) {
         if (this.isTimedTool(ingredient)) {
            event.getInventory().setResult((ItemStack)null);
            return;
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onGrindstonePrepare(PrepareGrindstoneEvent event) {
      if (this.isTimedTool(event.getInventory().getItem(0)) || this.isTimedTool(event.getInventory().getItem(1))) {
         event.setResult((ItemStack)null);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onSmithingPrepare(PrepareSmithingEvent event) {
      for(int i = 0; i < 3; ++i) {
         if (this.isTimedTool(event.getInventory().getItem(i))) {
            event.setResult((ItemStack)null);
            return;
         }
      }

   }

   private void runDrill(Player player, Block center, ItemStack tool, int slot) {
      if (this.isToolInSlot(player, slot, tool)) {
         Sound breakSound = this.resolveConfiguredSound("settings.drill-break-sound", "BLOCK_AMETHYST_BLOCK_BREAK");
         double dx = player.getEyeLocation().getDirection().getX();
         double dz = player.getEyeLocation().getDirection().getZ();
         boolean xDominant = Math.abs(dx) >= Math.abs(dz);
         if (xDominant) {
            for(int y = -1; y <= 1; ++y) {
               for(int z = -1; z <= 1 && this.canUseTool(tool); ++z) {
                  this.breakDrillBlock(player, center.getWorld().getBlockAt(center.getX(), center.getY() + y, center.getZ() + z), tool);
               }
            }
         } else {
            for(int x = -1; x <= 1; ++x) {
               for(int y = -1; y <= 1 && this.canUseTool(tool); ++y) {
                  this.breakDrillBlock(player, center.getWorld().getBlockAt(center.getX() + x, center.getY() + y, center.getZ()), tool);
               }
            }
         }

         this.updateToolInSlot(player, slot, tool);
         player.playSound(center.getLocation(), breakSound, 1.0F, 1.0F);
      }
   }

   private void breakDrillBlock(Player player, Block block, ItemStack tool) {
      if (block.getType() != Material.AIR && !this.blacklist.contains(block.getType())) {
         this.breakToolBlock(player, block, tool);
      }

   }

   private void runVeinMiner(Player player, Block start, ItemStack tool, int slot) {
      if (this.isToolInSlot(player, slot, tool)) {
         Material targetType = start.getType();
         if (!this.isVeinMineable(targetType)) {
            this.breakToolBlock(player, start, tool);
            this.updateToolInSlot(player, slot, tool);
         } else {
            int max = Math.max(1, this.plugin.getConfig().getInt("settings.veinminer-max-blocks", 48));
            int searchRadius = Math.max(1, this.plugin.getConfig().getInt("settings.veinminer-search-radius", 1));
            Queue<Block> queue = new ArrayDeque();
            Set<Long> visited = new HashSet(Math.min(max * 2, 512));
            List<Block> toBreak = new ArrayList(Math.min(max, 128));
            int totalExp = 0;
            queue.add(start);

            while(!queue.isEmpty() && toBreak.size() < max) {
               Block block = (Block)queue.poll();
               long key = this.blockKey(block);
               if (visited.add(key) && block.getType() == targetType) {
                  toBreak.add(block);

                  for(int[] offset : this.veinOffsets(searchRadius)) {
                     Block relative = block.getRelative(offset[0], offset[1], offset[2]);
                     if (relative.getType() == targetType) {
                        queue.add(relative);
                     }
                  }
               }
            }

            for(Block block : toBreak) {
               if (!this.canUseTool(tool)) {
                  break;
               }

               totalExp += this.breakToolBlock(player, block, tool);
            }

            this.awardVeinExperience(player, start.getLocation(), totalExp);
            this.updateToolInSlot(player, slot, tool);
            player.playSound(start.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0F, 1.0F);
         }

      }
   }

   private void runTreeChopper(Player player, Block start, ItemStack tool, int slot) {
      if (this.isToolInSlot(player, slot, tool)) {
         if (!this.isLogLike(start.getType())) {
            this.breakToolBlock(player, start, tool);
            this.updateToolInSlot(player, slot, tool);
         } else {
            int max = this.plugin.getConfig().getInt("settings.tree-max-blocks", 512);
            Queue<Block> queue = new ArrayDeque();
            Set<Long> visited = new HashSet(Math.min(max * 2, 2048));
            List<Block> toBreak = new ArrayList(Math.min(max, 128));
            queue.add(start);

            while(!queue.isEmpty() && toBreak.size() < max) {
               Block block = (Block)queue.poll();
               long key = this.blockKey(block);
               if (visited.add(key) && this.isLogLike(block.getType()) && !this.blacklist.contains(block.getType())) {
                  toBreak.add(block);

                  for(int[] offset : LOG_NEIGHBORS) {
                     Block relative = block.getRelative(offset[0], offset[1], offset[2]);
                     if (this.isLogLike(relative.getType())) {
                        queue.add(relative);
                     }
                  }
               }
            }

            for(Block block : toBreak) {
               if (!this.canUseTool(tool)) {
                  break;
               }

               this.breakToolBlock(player, block, tool);
            }

            this.updateToolInSlot(player, slot, tool);
         }

      }
   }

   private void drainWater(Player player, Block origin, int limit) {
      player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 1.0F, 1.0F);
      if (FoliaSupport.isFolia()) {
         WaterDrainState state = new WaterDrainState(limit);
         state.enqueue(origin.getLocation());
         this.drainWaterFolia(state);
      } else {
         Queue<Location> queue = new ArrayDeque();
         Set<Long> visited = new HashSet();
         queue.add(origin.getLocation());
         int drained = 0;

         while(!queue.isEmpty() && drained < limit) {
            Location location = (Location)queue.poll();
            long key = this.blockKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            if (visited.add(key)) {
               Block block = location.getBlock();
               if (this.isWater(block)) {
                  block.setType(Material.AIR, false);
                  ++drained;

                  for(BlockFace face : CARDINALS) {
                     queue.add(location.clone().add((double)face.getModX(), (double)face.getModY(), (double)face.getModZ()));
                  }
               }
            }
         }
      }

   }

   private void drainWaterFolia(WaterDrainState state) {
      Location next = state.pollNext();
      if (next != null) {
         FoliaSupport.runAtLocation(this.plugin, next, () -> {
            if (!state.isLimitReached()) {
               Block regional = next.getBlock();
               if (!this.isWater(regional)) {
                  this.drainWaterFolia(state);
               } else {
                  regional.setType(Material.AIR, false);
                  if (state.recordDrain()) {
                     for(BlockFace face : CARDINALS) {
                        state.enqueue(next.clone().add((double)face.getModX(), (double)face.getModY(), (double)face.getModZ()));
                     }
                  }

                  this.drainWaterFolia(state);
               }
            }

         });
      }

   }

   private int breakToolBlock(Player player, Block block, ItemStack tool) {
      if (this.canUseTool(tool) && block.getType() != Material.AIR) {
         Location dropLocation = block.getLocation().add((double)0.5F, (double)0.5F, (double)0.5F);

         for(ItemStack drop : block.getDrops(tool, player)) {
            if (drop != null && drop.getType() != Material.AIR && drop.getAmount() > 0) {
               block.getWorld().dropItemNaturally(dropLocation, drop);
            }
         }

         int exp = this.resolveExpDrop(block, player, tool);
         block.setType(Material.AIR, false);
         this.damageTool(tool);
         return exp;
      } else {
         return 0;
      }
   }

   private void damageTool(ItemStack tool) {
      ItemMeta meta = tool.getItemMeta();
      if (meta instanceof Damageable damageable) {
         int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
         boolean shouldDamage = unbreakingLevel <= 0 || ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) == 0;
         if (shouldDamage) {
            int newDamage = damageable.getDamage() + 1;
            if (newDamage >= tool.getType().getMaxDurability()) {
               tool.setAmount(0);
            } else {
               damageable.setDamage(newDamage);
               tool.setItemMeta(meta);
            }
         }
      }

   }

   private boolean canUseTool(ItemStack tool) {
      return tool != null && tool.getType() != Material.AIR && tool.getAmount() > 0;
   }

   private String getToolUid(ItemStack item) {
      return item != null && item.getType() != Material.AIR ? (String)NBT.get(item, (nbt) -> (String)nbt.getOrNull("tktool-uid", String.class)) : null;
   }

   private boolean sameTool(ItemStack current, ItemStack tool) {
      if (current != null && tool != null && current.getType() != Material.AIR) {
         String uid = this.getToolUid(tool);
         if (uid != null) {
            return uid.equals(this.getToolUid(current));
         } else {
            String id = this.getToolId(tool);
            return id != null && id.equals(this.getToolId(current)) && current.getType() == tool.getType();
         }
      } else {
         return false;
      }
   }

   private boolean isToolInSlot(Player player, int slot, ItemStack tool) {
      return this.sameTool(player.getInventory().getItem(slot), tool);
   }

   private void updateToolInSlot(Player player, int slot, ItemStack tool) {
      PlayerInventory inventory = player.getInventory();
      if (this.sameTool(inventory.getItem(slot), tool)) {
         inventory.setItem(slot, this.canUseTool(tool) ? tool : null);
      }

   }

   private void breakExpiredMainHandTool(Player player) {
      player.getInventory().setItemInMainHand((ItemStack)null);
      player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
   }

   private Block resolveBucketTarget(PlayerInteractEvent event) {
      Block target = event.getClickedBlock();
      if (target == null) {
         target = event.getPlayer().getTargetBlockExact(5, FluidCollisionMode.ALWAYS);
      }

      if (target == null) {
         return null;
      } else {
         Block center = target.getType() == Material.WATER ? target : target.getRelative(BlockFace.UP);
         return center.getType() == Material.WATER ? center : null;
      }
   }

   private Sound resolveConfiguredSound(String path, String fallbackName) {
      String configured = this.plugin.getConfig().getString(path, fallbackName);
      Sound resolved = this.resolveSound(configured);
      if (resolved != null) {
         return resolved;
      } else {
         this.plugin.getLogger().warning("Invalid sound '" + configured + "' for " + path + ", falling back to " + fallbackName);
         Sound fallback = this.resolveSound(fallbackName);
         return fallback != null ? fallback : Sound.BLOCK_AMETHYST_BLOCK_BREAK;
      }
   }

   private Sound resolveSound(String value) {
      if (value != null && !value.isBlank()) {
         try {
            return (Sound)Sound.class.getField(value.trim().toUpperCase(Locale.ROOT)).get((Object)null);
         } catch (ReflectiveOperationException var6) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            NamespacedKey directKey = normalized.contains(":") ? NamespacedKey.fromString(normalized) : NamespacedKey.minecraft(normalized.replace('_', '.'));
            if (directKey != null) {
               Sound direct = (Sound)Registry.SOUNDS.get(directKey);
               if (direct != null) {
                  return direct;
               }
            }

            NamespacedKey legacyKey = NamespacedKey.minecraft(normalized.replace('.', '_'));
            return (Sound)Registry.SOUNDS.get(legacyKey);
         }
      } else {
         return null;
      }
   }

   private Set<Material> loadBlacklist(TktoolsUpdate plugin) {
      List<String> entries = plugin.getConfig().getStringList("settings.blacklist-blocks");
      Set<Material> materials = new HashSet();

      for(String entry : entries) {
         Material material = Material.matchMaterial(entry);
         if (material == null) {
            plugin.getLogger().warning("Unknown blacklist material in config.yml: " + entry);
         } else {
            materials.add(material);
         }
      }

      if (materials.isEmpty()) {
         materials.add(Material.BEDROCK);
      }

      return Set.copyOf(materials);
   }

   private Set<String> loadBlacklistedWorlds(TktoolsUpdate plugin) {
      List<String> entries = plugin.getConfig().getStringList("settings.blacklist-worlds");
      Set<String> worlds = new HashSet();

      for(String entry : entries) {
         if (entry != null && !entry.isBlank()) {
            worlds.add(entry.toLowerCase(Locale.ROOT));
         }
      }

      return Set.copyOf(worlds);
   }

   private boolean isWorldBlacklisted(World world) {
      if (world == null) {
         return false;
      } else {
         String worldName = world.getName().toLowerCase(Locale.ROOT);
         if (this.blacklistedWorlds.contains(worldName)) {
            return true;
         } else {
            String worldKey = world.getKey().toString().toLowerCase(Locale.ROOT);
            return this.blacklistedWorlds.contains(worldKey) ? true : this.blacklistedWorlds.contains(world.getKey().getKey().toLowerCase(Locale.ROOT));
         }
      }
   }

   private boolean isWater(Block block) {
      if (block == null) {
         return false;
      } else {
         boolean var10000;
         if (block.getType() == Material.WATER) {
            var10000 = true;
         } else {
            BlockData var3 = block.getBlockData();
            if (var3 instanceof Waterlogged) {
               Waterlogged waterlogged = (Waterlogged)var3;
               if (waterlogged.isWaterlogged()) {
                  var10000 = true;
                  return var10000;
               }
            }

            var10000 = false;
         }

         return var10000;
      }
   }

   private boolean isLogLike(Material material) {
      return Tag.LOGS.isTagged(material) || material.name().endsWith("_STEM") || material.name().endsWith("_HYPHAE") || material.name().endsWith("_WOOD");
   }

   private boolean isVeinMineable(Material material) {
      if (material == null) {
         return false;
      } else {
         String name = material.name();
         return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
      }
   }

   private int resolveExpDrop(Block block, Player player, ItemStack tool) {
      if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
         return 0;
      } else {
         Material material = block.getType();
         ThreadLocalRandom random = ThreadLocalRandom.current();
         int var10000;
         switch (material) {
            case COAL_ORE:
            case DEEPSLATE_COAL_ORE:
               int var13 = random.nextInt(0, 3);
               var10000 = var13;
               break;
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
               int var12 = random.nextInt(3, 8);
               var10000 = var12;
               break;
            case LAPIS_ORE:
            case DEEPSLATE_LAPIS_ORE:
            case NETHER_QUARTZ_ORE:
            case NETHER_GOLD_ORE:
               int var11 = random.nextInt(2, 6);
               var10000 = var11;
               break;
            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
               int var10 = random.nextInt(1, 6);
               var10000 = var10;
               break;
            case ANCIENT_DEBRIS:
               int var6 = random.nextInt(2, 5);
               var10000 = var6;
               break;
            default:
               int exp = 0;

               try {
                  exp = (Integer)Block.class.getMethod("getExpDrop", Player.class, ItemStack.class).invoke(block, player, tool);
               } catch (ReflectiveOperationException var9) {
               }

               var10000 = exp;
         }

         return var10000;
      }
   }

   private void awardVeinExperience(Player player, Location origin, int totalExp) {
      if (totalExp > 0) {
         player.giveExp(totalExp);
         ExperienceOrb orb = (ExperienceOrb)origin.getWorld().spawn(origin.clone().add((double)0.5F, (double)0.5F, (double)0.5F), ExperienceOrb.class);
         orb.setExperience(totalExp);
         orb.setVelocity(orb.getVelocity().zero());
      }

   }

   private static int[][] createVeinNeighbors() {
      List<int[]> offsets = new ArrayList(26);

      for(int x = -1; x <= 1; ++x) {
         for(int y = -1; y <= 1; ++y) {
            for(int z = -1; z <= 1; ++z) {
               if (x != 0 || y != 0 || z != 0) {
                  offsets.add(new int[]{x, y, z});
               }
            }
         }
      }

      return (int[][])offsets.toArray(new int[0][]);
   }

   private int[][] veinOffsets(int searchRadius) {
      return searchRadius <= 1 ? VEIN_NEIGHBORS : this.createSearchRadiusOffsets(searchRadius);
   }

   private int[][] createSearchRadiusOffsets(int searchRadius) {
      List<int[]> offsets = new ArrayList();

      for(int x = -searchRadius; x <= searchRadius; ++x) {
         for(int y = -searchRadius; y <= searchRadius; ++y) {
            for(int z = -searchRadius; z <= searchRadius; ++z) {
               if (x != 0 || y != 0 || z != 0) {
                  offsets.add(new int[]{x, y, z});
               }
            }
         }
      }

      return (int[][])offsets.toArray(new int[0][]);
   }

   private String getToolId(ItemStack item) {
      return item != null && item.getType() != Material.AIR ? (String)NBT.get(item, (nbt) -> (String)nbt.getOrNull("tktool-id", String.class)) : null;
   }

   private long blockKey(Block block) {
      return this.blockKey(block.getX(), block.getY(), block.getZ());
   }

   private long blockKey(int x, int y, int z) {
      long packedY = (long)(y + 2048) & 8191L;
      return ((long)x & 67108863L) << 39 | ((long)z & 67108863L) << 13 | packedY;
   }

   static {
      CARDINALS = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
      LOG_NEIGHBORS = new int[][]{{0, 1, 0}, {0, 1, 1}, {0, 1, -1}, {1, 1, 0}, {-1, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 1}, {1, 1, -1}, {-1, 1, 1}, {-1, 1, -1}, {0, 2, 0}, {1, 2, 0}, {-1, 2, 0}, {0, 2, 1}, {0, 2, -1}};
      VEIN_NEIGHBORS = createVeinNeighbors();
   }

   private final class WaterDrainState {
      private final Queue<Location> queue = new ArrayDeque();
      private final Set<Long> visited = new HashSet();
      private final int limit;
      private int drained;

      private WaterDrainState(int limit) {
         this.limit = limit;
      }

      private synchronized void enqueue(Location location) {
         this.queue.add(location);
      }

      private synchronized Location pollNext() {
         while(true) {
            if (!this.queue.isEmpty() && this.drained < this.limit) {
               Location location = (Location)this.queue.poll();
               if (location == null) {
                  return null;
               }

               long key = ToolListener.this.blockKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
               if (!this.visited.add(key)) {
                  continue;
               }

               return location;
            }

            return null;
         }
      }

      private synchronized boolean recordDrain() {
         if (this.drained >= this.limit) {
            return false;
         } else {
            ++this.drained;
            return this.drained < this.limit;
         }
      }

      private synchronized boolean isLimitReached() {
         return this.drained >= this.limit;
      }
   }
}
