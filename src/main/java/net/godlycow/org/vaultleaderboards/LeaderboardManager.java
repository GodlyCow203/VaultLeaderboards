package net.godlycow.org.vaultleaderboards;

import net.godlycow.org.vaultleaderboards.database.DatabaseConfig;
import net.godlycow.org.vaultleaderboards.database.DatabaseManager;
import net.godlycow.org.vaultleaderboards.database.SyncManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LeaderboardManager {
   private final VaultLeaderboards plugin;
   private final Economy economy;
   private final MiniMessage mini = MiniMessage.miniMessage();

   private DatabaseConfig dbConfig;
   private DatabaseManager dbManager;
   private SyncManager syncManager;
   private boolean useDatabase;

   private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
   private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();

   public LeaderboardManager(VaultLeaderboards plugin, Economy economy) {
      this.plugin = plugin;
      this.economy = economy;
      setupDatabase();
   }

   private void setupDatabase() {
      this.dbConfig = new DatabaseConfig(plugin);
      this.useDatabase = dbConfig.isEnabled();

      if (useDatabase) {
         this.dbManager = new DatabaseManager(plugin, dbConfig);
         this.useDatabase = dbManager.connect();

         if (this.useDatabase) {
            this.syncManager = new SyncManager(plugin, dbManager);
            this.syncManager.startSyncTask();
            plugin.getLogger().info("Database mode enabled - Cross-server leaderboards active!");

            loadFromDatabase();
         } else {
            plugin.getLogger().warning("Database connection failed - Falling back to local mode");
         }
      }
   }

   private void loadFromDatabase() {
      Map<UUID, DatabaseManager.PlayerBalance> top = dbManager.getTopBalancesSync(1000, 0);
      top.forEach((uuid, pb) -> {
         balances.put(uuid, pb.getBalance());
         playerNames.put(uuid, pb.getPlayerName());
      });
      plugin.getLogger().info("Loaded " + top.size() + " players from database");
   }

   public void shutdown() {
      if (syncManager != null) {
         syncManager.stopSyncTask();
      }
      if (dbManager != null) {
         dbManager.disconnect();
      }
   }

   public void updateLeaderboard() {
      if (useDatabase && dbManager.isConnected()) {
         loadFromDatabase();
         return;
      }

      this.balances.clear();
      this.playerNames.clear();

      OfflinePlayer[] offlinePlayerArray = Bukkit.getOfflinePlayers();

      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         for (OfflinePlayer player : offlinePlayerArray) {
            // if the player name is null it gets skipped
            String playerName = player.getName();
            if (playerName == null) continue;

            double balance = sGetBalance(player);

            Bukkit.getScheduler().runTask(this.plugin, () -> {
               this.balances.put(player.getUniqueId(), balance);
               this.playerNames.put(player.getUniqueId(), playerName);
            });

            if (this.plugin.getConfig().getBoolean("settings.debug", false)) {
               this.plugin.getLogger().info("Loaded " + playerName + " → " + balance);
            }
         }
      });
   }


   private double sGetBalance(OfflinePlayer player) {
      try {
         return this.economy.getBalance(player);
      } catch (NullPointerException e) {
         return 0.0;
      } catch (Exception e) {
         return 0.0;
      }
   }

   public Component getTopPlayerComponent(int rank) {
      int limit = plugin.getConfig().getInt("settings.top-limit", 10);

      if (useDatabase && dbManager.isConnected()) {
         Map<UUID, DatabaseManager.PlayerBalance> top = dbManager.getTopBalancesSync(limit, 0);

         if (rank > 0 && rank <= top.size()) {
            DatabaseManager.PlayerBalance pb = new ArrayList<>(top.values()).get(rank - 1);
            return formatTopPlayer(rank, pb.getPlayerName(), pb.getBalance());
         }
      }

      List<Map.Entry<UUID, Double>> sorted = this.balances.entrySet().stream()
              .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
              .limit(limit)
              .collect(Collectors.toList());

      if (rank > 0 && rank <= sorted.size()) {
         Map.Entry<UUID, Double> entry = sorted.get(rank - 1);
         String name = playerNames.getOrDefault(entry.getKey(), "Unknown");
         return formatTopPlayer(rank, name, entry.getValue());
      } else {
         String naTemplate = this.plugin.getConfig().getString("placeholders.not-available", "<red>N/A</red>");
         return this.mini.deserialize(naTemplate);
      }
   }

   public Component getPlayerRankComponent(String name) {
      OfflinePlayer player = Bukkit.getOfflinePlayer(name);

      if (useDatabase && dbManager.isConnected()) {
         int rank = dbManager.getPlayerRankSync(player.getUniqueId());
         Optional<DatabaseManager.PlayerBalance> pb = dbManager.getPlayerBalanceSync(player.getUniqueId());

         if (pb.isPresent() && rank > 0) {
            String template = this.plugin.getConfig().getString("placeholders.player",
                    "<yellow><player></yellow> is <green>#<rank></green> with <aqua>$<balance>");
            String result = template
                    .replace("<player>", name)
                    .replace("<rank>", String.valueOf(rank))
                    .replace("<balance>", this.formatBalance(pb.get().getBalance()));
            return this.mini.deserialize(result);
         }
      }

      if (!this.balances.containsKey(player.getUniqueId())) {
         String naTemplate = this.plugin.getConfig().getString("placeholders.not-available", "<red>N/A</red>");
         return this.mini.deserialize(naTemplate);
      }

      List<Map.Entry<UUID, Double>> sorted = this.balances.entrySet().stream()
              .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
              .collect(Collectors.toList());

      int rank = 1;
      for (Map.Entry<UUID, Double> entry : sorted) {
         if (entry.getKey().equals(player.getUniqueId())) break;
         rank++;
      }

      String template = this.plugin.getConfig().getString("placeholders.player",
              "<yellow><player></yellow> is <green>#<rank></green> with <aqua>$<balance>");
      String result = template
              .replace("<player>", name)
              .replace("<rank>", String.valueOf(rank))
              .replace("<balance>", this.formatBalance(this.balances.get(player.getUniqueId())));
      return this.mini.deserialize(result);
   }

   public Component getServerAverageComponent() {
      if (useDatabase && dbManager.isConnected()) {
         double average = dbManager.getServerAverageSync();
         return formatServerAverage(average);
      }

      if (this.balances.isEmpty()) {
         String naTemplate = this.plugin.getConfig().getString("placeholders.not-available", "<red>N/A</red>");
         return this.mini.deserialize(naTemplate);
      }

      double average = this.balances.values().stream()
              .mapToDouble(Double::doubleValue)
              .average()
              .orElse(0.0D);
      return formatServerAverage(average);
   }

   public Component getTopEarnerComponent(int rank, int days) {
      if (!useDatabase || !dbManager.isConnected()) {
         return this.mini.deserialize("<red>Time-based leaderboards require database</red>");
      }

      try {
         List<DatabaseManager.BalanceChange> earners =
                 dbManager.getTopEarners(days, plugin.getConfig().getInt("settings.top-limit", 10)).get();

         if (rank > 0 && rank <= earners.size()) {
            DatabaseManager.BalanceChange earner = earners.get(rank - 1);
            String template = this.plugin.getConfig().getString("placeholders.top-earner",
                    "<yellow><rank>. <green><player></green> +<aqua>$<amount></aqua> (<days>d)");
            String result = template
                    .replace("<rank>", String.valueOf(rank))
                    .replace("<player>", earner.getPlayerName())
                    .replace("<amount>", this.formatBalance(earner.getAmount()))
                    .replace("<days>", String.valueOf(days));
            return this.mini.deserialize(result);
         }
      } catch (Exception e) {
         plugin.getLogger().warning("Failed to get top earners: " + e.getMessage());
      }

      String naTemplate = this.plugin.getConfig().getString("placeholders.not-available", "<red>N/A</red>");
      return this.mini.deserialize(naTemplate);
   }

   public void notifyBalanceChange(UUID uuid, String playerName, double newBalance) {
      if (useDatabase && syncManager != null) {
         syncManager.queueUpdate(uuid, playerName, newBalance);
      }
      balances.put(uuid, newBalance);
      playerNames.put(uuid, playerName);
   }

   private Component formatTopPlayer(int rank, String name, double balance) {
      String template = this.plugin.getConfig().getString("placeholders.top",
              "<yellow><rank>. <green><player></green> - <aqua>$<balance>");
      String result = template
              .replace("<rank>", String.valueOf(rank))
              .replace("<player>", name != null ? name : "Unknown")
              .replace("<balance>", this.formatBalance(balance));
      return this.mini.deserialize(result);
   }

   private Component formatServerAverage(double average) {
      String template = this.plugin.getConfig().getString("placeholders.server-average",
              "<yellow>Server Average: <aqua>$<balance>");
      String result = template.replace("<balance>", this.formatBalance(average));
      return this.mini.deserialize(result);
   }

   private String formatBalance(double balance) {
      if (balance >= 1.0E12D) {
         return String.format("%.2fT", balance / 1.0E12D);
      } else if (balance >= 1.0E9D) {
         return String.format("%.2fB", balance / 1.0E9D);
      } else if (balance >= 1000000.0D) {
         return String.format("%.2fM", balance / 1000000.0D);
      } else if (balance >= 1000.0D) {
         return String.format("%.2fk", balance / 1000.0D);
      } else {
         return String.format("%.2f", balance);
      }
   }

   public boolean isDatabaseEnabled() {
      return useDatabase;
   }

   public DatabaseManager getDatabaseManager() {
      return dbManager;
   }
}