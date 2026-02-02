package net.godlycow.org.vaultleaderboards.database;

import net.godlycow.org.vaultleaderboards.VaultLeaderboards;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SyncManager {
    private final VaultLeaderboards plugin;
    private final DatabaseManager dbManager;
    private final Map<UUID, Double> pendingUpdates = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastKnownBalances = new ConcurrentHashMap<>();

    private boolean syncEnabled;
    private long syncInterval;
    private int syncTaskId = -1;

    public SyncManager(VaultLeaderboards plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        loadConfig();
    }

    public void loadConfig() {
        this.syncEnabled = plugin.getConfig().getBoolean("database.sync.enabled", true);
        this.syncInterval = plugin.getConfig().getLong("database.sync.interval-seconds", 30);
    }

    public void startSyncTask() {
        if (!syncEnabled || !dbManager.isConnected()) return;

        syncTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            performFullSync();
        }, 20L, syncInterval * 20L).getTaskId();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            flushPendingUpdates();
        }, 20L, 5 * 20L).getTaskId();
    }

    public void stopSyncTask() {
        if (syncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(syncTaskId);
            syncTaskId = -1;
        }
        flushPendingUpdates();
    }

    public void performFullSync() {
        if (!dbManager.isConnected()) return;

        dbManager.getTopBalances(1000, 0).thenAccept(topPlayers -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                topPlayers.forEach((uuid, pb) -> {
                    lastKnownBalances.put(uuid, pb.getBalance());
                });
            });
        });
    }

    public void queueUpdate(UUID uuid, String playerName, double balance) {
        Double lastBalance = lastKnownBalances.get(uuid);

        if (lastBalance == null || Math.abs(lastBalance - balance) > 0.01) {
            pendingUpdates.put(uuid, balance);
            lastKnownBalances.put(uuid, balance);

            dbManager.updateBalance(uuid, playerName, balance);
        }
    }

    private void flushPendingUpdates() {
        if (pendingUpdates.isEmpty() || !dbManager.isConnected()) return;

        Map<UUID, Double> updates = new ConcurrentHashMap<>(pendingUpdates);
        pendingUpdates.clear();

        updates.forEach((uuid, balance) -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            String name = player.getName() != null ? player.getName() : "Unknown";
            dbManager.updateBalance(uuid, name, balance);
        });
    }

    public double getCachedBalance(UUID uuid) {
        return lastKnownBalances.getOrDefault(uuid, 0.0);
    }

    public CompletableFuture<Double> getLiveBalance(UUID uuid) {
        return dbManager.getPlayerBalance(uuid)
                .thenApply(opt -> opt.map(DatabaseManager.PlayerBalance::getBalance).orElse(0.0));
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }
}