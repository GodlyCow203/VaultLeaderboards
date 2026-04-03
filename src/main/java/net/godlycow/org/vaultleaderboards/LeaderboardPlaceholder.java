package net.godlycow.org.vaultleaderboards;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class LeaderboardPlaceholder extends PlaceholderExpansion {
   private final VaultLeaderboards plugin;
   private final LeaderboardManager manager;

   public LeaderboardPlaceholder(VaultLeaderboards plugin, LeaderboardManager manager) {
      this.plugin = plugin;
      this.manager = manager;
   }

   @NotNull
   public String getIdentifier() {
      return "vaultleaderboards";
   }

   @NotNull
   public String getAuthor() {
      return "_GodlyCow";
   }

   @NotNull
   public String getVersion() {
      return this.plugin.getDescription().getVersion();
   }

   public boolean persist() {
      return true;
   }

   public String onRequest(OfflinePlayer player, @NotNull String identifier) {
      if (identifier.startsWith("top_")) {
         try {
            int rank = Integer.parseInt(identifier.split("_")[1]);
            return LegacyComponentSerializer.legacySection().serialize(this.manager.getTopPlayerComponent(rank));
         } catch (Exception var4) {
            return "§cInvalid rank";
         }
      } else if (identifier.equalsIgnoreCase("server_average")) {
         return LegacyComponentSerializer.legacySection().serialize(this.manager.getServerAverageComponent());
      } else if (identifier.startsWith("topearner_")) {
         try {
            String[] parts = identifier.split("_");
            int rank = Integer.parseInt(parts[1]);
            int days = parts.length > 2 ? Integer.parseInt(parts[2].replace("d", "")) : 7;
            return LegacyComponentSerializer.legacySection().serialize(this.manager.getTopEarnerComponent(rank, days));
         } catch (Exception e) {
            return "§cInvalid format (use topearner_1_7d)";
         }
      } else if (identifier.startsWith("daily_")) {
         try {
            int rank = Integer.parseInt(identifier.split("_")[1]);
            return LegacyComponentSerializer.legacySection().serialize(this.manager.getDailyTopEarnerComponent(rank));
         } catch (Exception e) {
            return "§cInvalid format (use daily_1)";
         }
      } else if (identifier.startsWith("weekly_")) {
         try {
            int rank = Integer.parseInt(identifier.split("_")[1]);
            return LegacyComponentSerializer.legacySection().serialize(this.manager.getWeeklyTopEarnerComponent(rank));
         } catch (Exception e) {
            return "§cInvalid format (use weekly_1)";
         }
      } else if (identifier.startsWith("monthly_")) {
         try {
            int rank = Integer.parseInt(identifier.split("_")[1]);
            return LegacyComponentSerializer.legacySection().serialize(this.manager.getMonthlyTopEarnerComponent(rank));
         } catch (Exception e) {
            return "§cInvalid format (use monthly_1)";
         }
      } else {
         return identifier.matches("^[A-Za-z0-9_]{3,16}$") ?
                 LegacyComponentSerializer.legacySection().serialize(this.manager.getPlayerRankComponent(identifier)) : null;
      }
   }
}