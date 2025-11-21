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
      } else {
         return identifier.matches("^[A-Za-z0-9_]{3,16}$") ? LegacyComponentSerializer.legacySection().serialize(this.manager.getPlayerRankComponent(identifier)) : null;
      }
   }
}
