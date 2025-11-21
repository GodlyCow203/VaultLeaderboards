package net.godlycow.org.vaultleaderboards;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class LeaderboardManager {
   private final VaultLeaderboards plugin;
   private final Economy economy;
   private final MiniMessage mini = MiniMessage.miniMessage();
   private final Map<UUID, Double> balances = new HashMap();

   public LeaderboardManager(VaultLeaderboards plugin, Economy economy) {
      this.plugin = plugin;
      this.economy = economy;
   }

   public void updateLeaderboard() {
      this.balances.clear();
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         OfflinePlayer[] offlinePlayerArray = Bukkit.getOfflinePlayers();
         int n = offlinePlayerArray.length;

         for(int i = 0; i < n; ++i) {
            OfflinePlayer player = offlinePlayerArray[i];
            double balance = this.economy.getBalance(player);
            this.balances.put(player.getUniqueId(), balance);
            if (this.plugin.getConfig().getBoolean("settings.debug", false)) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = player.getName();
               var10000.info("Loaded " + var10001 + " → " + balance);
            }
         }

      });
   }

   public Component getTopPlayerComponent(int rank) {
      List sorted = (List)this.balances.entrySet().stream().sorted((a, b) -> {
         return Double.compare((Double)b.getValue(), (Double)a.getValue());
      }).limit((long)this.plugin.getConfig().getInt("settings.top-limit", 10)).collect(Collectors.toList());
      if (rank > 0 && rank <= sorted.size()) {
         Entry entry = (Entry)sorted.get(rank - 1);
         OfflinePlayer player = Bukkit.getOfflinePlayer((UUID)entry.getKey());
         String name = player.getName() != null ? player.getName() : "Unknown";
         String template = this.plugin.getConfig().getString("placeholders.top", "<yellow><rank>. <green><player></green> - <aqua>$<balance>");
         String result = template.replace("<rank>", String.valueOf(rank)).replace("<player>", name).replace("<balance>", this.formatBalance((Double)entry.getValue()));
         return this.mini.deserialize(result);
      } else {
         String naTemplate = this.plugin.getConfig().getString("placeholders.not-available", "<red>N/A</red>");
         return this.mini.deserialize(naTemplate);
      }
   }

   public Component getPlayerRankComponent(String name) {
      OfflinePlayer player = Bukkit.getOfflinePlayer(name);
      if (!this.balances.containsKey(player.getUniqueId())) {
         String naTemplate = this.plugin.getConfig().getString("placeholders.not-available", "<red>N/A</red>");
         return this.mini.deserialize(naTemplate);
      } else {
         List sorted = (List)this.balances.entrySet().stream().sorted((a, b) -> {
            return Double.compare((Double)b.getValue(), (Double)a.getValue());
         }).collect(Collectors.toList());
         int rank = 1;

         for(Iterator iterator = sorted.iterator(); iterator.hasNext() && !((UUID)((Entry)iterator.next()).getKey()).equals(player.getUniqueId()); ++rank) {
         }

         String template = this.plugin.getConfig().getString("placeholders.player", "<yellow><player></yellow> is <green>#<rank></green> with <aqua>$<balance>");
         String result = template.replace("<player>", name).replace("<rank>", String.valueOf(rank)).replace("<balance>", this.formatBalance((Double)this.balances.get(player.getUniqueId())));
         return this.mini.deserialize(result);
      }
   }

   public Component getServerAverageComponent() {
      if (this.balances.isEmpty()) {
         String naTemplate = this.plugin.getConfig().getString("placeholders.not-available", "<red>N/A</red>");
         return this.mini.deserialize(naTemplate);
      } else {
         double average = this.balances.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0D);
         String template = this.plugin.getConfig().getString("placeholders.server-average", "<yellow>Server Average: <aqua>$<balance>");
         String result = template.replace("<balance>", this.formatBalance(average));
         return this.mini.deserialize(result);
      }
   }

   private String formatBalance(double balance) {
      if (balance >= 1.0E9D) {
         return String.format("%.2fB", balance / 1.0E9D);
      } else if (balance >= 1000000.0D) {
         return String.format("%.2fM", balance / 1000000.0D);
      } else {
         return balance >= 1000.0D ? String.format("%.2fk", balance / 1000.0D) : String.format("%.2f", balance);
      }
   }
}