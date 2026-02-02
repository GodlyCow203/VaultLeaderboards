package net.godlycow.org.vaultleaderboards;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public final class VaultLeaderboards extends JavaPlugin implements Listener {
   private static VaultLeaderboards instance;
   private Economy economy;
   private LeaderboardManager leaderboardManager;
   public static final String PREFIX = "<dark_gray>[<gold>VL</gold>]</dark_gray>";
   private static final String MODRINTH_PROJECT_ID = "qlRnqYv7";
   private String latestVersion = null;
   private String updateUrl = null;

   public void onEnable() {
      instance = this;
      saveDefaultConfig();

      if (!this.setupEconomy()) {
         this.getLogger().severe("Vault or an economy plugin not found! Disabling...");
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }

      this.leaderboardManager = new LeaderboardManager(this, this.economy);
      this.leaderboardManager.updateLeaderboard();

      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         (new LeaderboardPlaceholder(this, this.leaderboardManager)).register();
         this.getLogger().info("Registered PlaceholderAPI placeholders.");
      } else {
         this.getLogger().warning("PlaceholderAPI not found — placeholders won't work.");
      }

      if (this.getConfig().getBoolean("settings.check-updates", true)) {
         this.checkForUpdates();
      }

      Bukkit.getPluginManager().registerEvents(this, this);
      this.getCommand("vaultleaderboards").setTabCompleter(new VaultLeaderboards.VaultLeaderboardsTabCompleter(this));

      long refreshSeconds = this.getConfig().getLong("settings.refresh-interval", 60L);
      Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
         this.leaderboardManager.updateLeaderboard();
      }, 20L, refreshSeconds * 20L);

      this.getCommand("vaultleaderboards").setExecutor((sender, command, label, args) -> {
         return this.handleCommand(sender, args);
      });

      int pluginId = 27346;
      new Metrics(this, pluginId);
      this.getLogger().info("VaultLeaderboards enabled successfully!" +
              (leaderboardManager.isDatabaseEnabled() ? " [Database Mode]" : " [Local Mode]"));
   }

   public void onDisable() {
      if (leaderboardManager != null) {
         leaderboardManager.shutdown();
      }
      this.getLogger().info("VaultLeaderboards disabled.");
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();

      if (leaderboardManager.isDatabaseEnabled()) {
         Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
            double balance = economy.getBalance(player);
            leaderboardManager.notifyBalanceChange(player.getUniqueId(), player.getName(), balance);
         }, 20L);
      }

      if (player.isOp() && this.getConfig().getBoolean("settings.check-updates", true)
              && this.getConfig().getBoolean("settings.notify-op-updates", true)) {
         if (this.latestVersion != null) {
            this.sendVersionUpdate(player);
         } else {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
               try {
                  URL url = new URL("https://api.modrinth.com/v2/project/qlRnqYv7/version");
                  HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                  connection.setRequestProperty("User-Agent", "VaultLeaderboards/" + this.getDescription().getVersion());
                  connection.setConnectTimeout(5000);
                  connection.setReadTimeout(5000);
                  JsonElement json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream()));
                  if (json.isJsonArray()) {
                     JsonArray versions = json.getAsJsonArray();
                     if (versions.size() > 0) {
                        JsonElement latest = versions.get(0);
                        String version = latest.getAsJsonObject().get("version_number").getAsString();
                        String urlStr = "https://modrinth.com/plugin/vaultleaderboards/version/" + version;
                        Bukkit.getScheduler().runTask(this, () -> {
                           this.latestVersion = version;
                           this.updateUrl = urlStr;
                           this.sendVersionUpdate(player);
                        });
                     }
                  }
                  connection.disconnect();
               } catch (Exception var9) {
                  this.getLogger().warning("Could not check for updates: " + var9.getMessage());
               }
            });
         }
      }
   }

   private void sendVersionUpdate(CommandSender sender) {
      String currentVersion = this.getDescription().getVersion();
      if (this.latestVersion != null && !currentVersion.equals(this.latestVersion)) {
         this.sendMessage(sender, this.getConfig().getString("messages.version.available", "<yellow>New version available: <green><new-version></green>").replace("<new-version>", this.latestVersion));
         if (this.updateUrl != null) {
            this.sendMessage(sender, this.getConfig().getString("messages.version.download", "<aqua>Download: <url>").replace("<url>", this.updateUrl));
         }
      } else if (this.latestVersion != null) {
         this.sendMessage(sender, this.getConfig().getString("messages.version.up-to-date", "<green>You are running the latest version!</green>"));
      } else {
         this.sendMessage(sender, this.getConfig().getString("messages.version.error", "<red>Failed to check for updates.</red>"));
      }
   }

   private boolean setupEconomy() {
      if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
         return false;
      }
      RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
      if (rsp == null) {
         return false;
      }
      this.economy = rsp.getProvider();
      return this.economy != null;
   }

   private boolean handleCommand(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.sendMessage(sender, "<gold>VaultLeaderboards by _GodlyCow");
         this.sendMessage(sender, "<gray>Use /vaultleaderboards help");
         if (leaderboardManager.isDatabaseEnabled()) {
            this.sendMessage(sender, "<green>Status: <aqua>Connected to database");
         }
         return true;
      }

      String var3 = args[0].toLowerCase();
      byte var4 = -1;
      switch(var3) {
         case "help":
            var4 = 0;
            break;
         case "reload":
            var4 = 1;
            break;
         case "version":
            var4 = 2;
            break;
         case "status":
            var4 = 3;
            break;
         case "sync":
            var4 = 4;
            break;
         case "top":
            var4 = 5;
      }

      switch(var4) {
         case 0:
            if (!this.hasPermission(sender, "vaultleaderboards.help")) {
               this.sendMessage(sender, this.getConfig().getString("messages.no-permission", "<red>You lack permission for this command.</red>"));
               return true;
            }
            this.sendHelpMessage(sender);
            return true;
         case 1:
            if (!this.hasPermission(sender, "vaultleaderboards.reload")) {
               this.sendMessage(sender, this.getConfig().getString("messages.no-permission", "<red>You lack permission for this command.</red>"));
               return true;
            }
            this.reloadConfig();
            this.leaderboardManager.updateLeaderboard();
            this.sendMessage(sender, this.getConfig().getString("messages.reload", "<green>Configuration reloaded!</green>"));
            return true;
         case 2:
            if (!this.hasPermission(sender, "vaultleaderboards.version")) {
               this.sendMessage(sender, this.getConfig().getString("messages.no-permission", "<red>You lack permission for this command.</red>"));
               return true;
            }
            this.sendVersionInfo(sender);
            return true;
         case 3:
            if (!this.hasPermission(sender, "vaultleaderboards.status")) {
               this.sendMessage(sender, this.getConfig().getString("messages.no-permission", "<red>You lack permission for this command.</red>"));
               return true;
            }
            this.sendStatus(sender);
            return true;
         case 4:
            if (!this.hasPermission(sender, "vaultleaderboards.sync")) {
               this.sendMessage(sender, this.getConfig().getString("messages.no-permission", "<red>You lack permission for this command.</red>"));
               return true;
            }
            this.forceSync(sender);
            return true;
         case 5:
            if (!this.hasPermission(sender, "vaultleaderboards.top")) {
               this.sendMessage(sender, this.getConfig().getString("messages.no-permission", "<red>You lack permission for this command.</red>"));
               return true;
            }
            int page = args.length > 1 ? Integer.parseInt(args[1]) : 1;
            this.showTop(sender, page);
            return true;
         default:
            this.sendMessage(sender, "<red>Unknown command. Use /vaultleaderboards help");
            return true;
      }
   }

   private void sendStatus(CommandSender sender) {
      this.sendMessage(sender, "<gold>=== VaultLeaderboards Status ===");
      this.sendMessage(sender, "<yellow>Database Mode: " + (leaderboardManager.isDatabaseEnabled() ? "<green>Enabled" : "<gray>Disabled"));
      if (leaderboardManager.isDatabaseEnabled()) {
         this.sendMessage(sender, "<yellow>Connection: <green>Active");
         this.sendMessage(sender, "<yellow>Use /vaultleaderboards sync to force synchronization");
      }
   }

   private void forceSync(CommandSender sender) {
      if (!leaderboardManager.isDatabaseEnabled()) {
         this.sendMessage(sender, "<red>Database mode is not enabled!");
         return;
      }
      this.sendMessage(sender, "<yellow>Forcing database synchronization...");
      Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
         leaderboardManager.updateLeaderboard();
         Bukkit.getScheduler().runTask(this, () -> {
            this.sendMessage(sender, "<green>Synchronization complete!");
         });
      });
   }

   private void showTop(CommandSender sender, int page) {
      int perPage = 10;
      int start = (page - 1) * perPage;

      this.sendMessage(sender, "<gold>=== Top Balances (Page " + page + ") ===");

      for (int i = 1; i <= perPage; i++) {
         int rank = start + i;
         Component line = leaderboardManager.getTopPlayerComponent(rank);
         String legacy = LegacyComponentSerializer.legacySection().serialize(line);
         sender.sendMessage(legacy);
      }
   }

   private void sendHelpMessage(CommandSender sender) {
      List<String> help = this.getConfig().getStringList("messages.help");
      if (help.isEmpty()) {
         this.sendMessage(sender, "<gold>Available Commands:");
         this.sendMessage(sender, "<yellow>/vl help <gray>- Show this help");
         this.sendMessage(sender, "<yellow>/vl reload <gray>- Reload configuration");
         this.sendMessage(sender, "<yellow>/vl version <gray>- Check version and updates");
         this.sendMessage(sender, "<yellow>/vl status <gray>- Check database status");
         this.sendMessage(sender, "<yellow>/vl sync <gray>- Force database sync");
         this.sendMessage(sender, "<yellow>/vl top [page] <gray>- Show top balances");
      } else {
         for (String line : help) {
            this.sendMessage(sender, line);
         }
      }
   }

   private void sendVersionInfo(CommandSender sender) {
      String currentVersion = this.getDescription().getVersion();
      this.sendMessage(sender, "<gold>VaultLeaderboards <gray>v" + currentVersion + "</gray></gold>");
      if (this.latestVersion == null) {
         this.sendMessage(sender, this.getConfig().getString("messages.version.checking", "<gray>Checking for updates...</gray>"));
         Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            this.checkForUpdates();
            Bukkit.getScheduler().runTask(this, () -> {
               this.sendVersionUpdate(sender);
            });
         });
      } else {
         this.sendVersionUpdate(sender);
      }
   }

   private void checkForUpdates() {
      Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
         try {
            URL url = new URL("https://api.modrinth.com/v2/project/qlRnqYv7/version");
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestProperty("User-Agent", "VaultLeaderboards/" + this.getDescription().getVersion());
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream()));
            if (json.isJsonArray()) {
               JsonArray versions = json.getAsJsonArray();
               if (versions.size() > 0) {
                  JsonElement latest = versions.get(0);
                  this.latestVersion = latest.getAsJsonObject().get("version_number").getAsString();
                  this.updateUrl = "https://modrinth.com/plugin/vaultleaderboards/version/" + this.latestVersion;
               }
            }
            connection.disconnect();
         } catch (Exception var6) {
            this.getLogger().warning("Could not check for updates: " + var6.getMessage());
         }
      });
   }

   private boolean hasPermission(CommandSender sender, String permission) {
      return sender.hasPermission(permission) || sender.isOp();
   }

   private void sendMessage(CommandSender sender, String message) {
      String prefix = this.getConfig().getString("messages.prefix", "<dark_gray>[<gold>VL</gold>]</dark_gray>");
      String fullMessage = prefix + " " + message;
      if (sender instanceof Player) {
         ((Player)sender).sendMessage(LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(fullMessage)));
      } else {
         sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(fullMessage)));
      }
   }

   public static VaultLeaderboards getInstance() {
      return instance;
   }

   private class VaultLeaderboardsTabCompleter implements TabCompleter {
      private VaultLeaderboardsTabCompleter(final VaultLeaderboards param1) {
      }

      public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
         if (args.length == 1) {
            List<String> suggestions = Arrays.asList("help", "reload", "version", "status", "sync", "top");
            return suggestions.stream()
                    .filter((s) -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
         } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            return Arrays.asList("1", "2", "3", "4", "5");
         }
         return Collections.emptyList();
      }
   }
}