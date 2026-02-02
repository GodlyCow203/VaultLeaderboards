package net.godlycow.org.vaultleaderboards.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.godlycow.org.vaultleaderboards.VaultLeaderboards;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class DatabaseManager {
    private final VaultLeaderboards plugin;
    private final DatabaseConfig config;
    private HikariDataSource dataSource;
    private boolean connected = false;

    private final Map<UUID, PlayerBalance> localCache = new ConcurrentHashMap<>();

    public DatabaseManager(VaultLeaderboards plugin, DatabaseConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean connect() {
        if (!config.isEnabled()) {
            plugin.getLogger().info("Database disabled, using local storage only.");
            return false;
        }

        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(config.getJdbcUrl());
            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
            hikariConfig.setMaximumPoolSize(config.getMaxConnections());
            hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
            hikariConfig.setPoolName("VaultLeaderboardsPool");

            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(hikariConfig);

            createTables();
            connected = true;
            plugin.getLogger().info("Successfully connected to MySQL database!");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to MySQL database!", e);
            connected = false;
            return false;
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed.");
        }
        connected = false;
    }

    private void createTables() throws SQLException {
        String balancesTable = getTableName("balances");
        String historyTable = getTableName("balance_history");

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16) NOT NULL,
                    balance DECIMAL(20, 2) NOT NULL DEFAULT 0.00,
                    last_seen BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_balance (balance DESC),
                    INDEX idx_last_seen (last_seen)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """, balancesTable));

            stmt.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    old_balance DECIMAL(20, 2) NOT NULL,
                    new_balance DECIMAL(20, 2) NOT NULL,
                    change_amount DECIMAL(20, 2) NOT NULL,
                    timestamp BIGINT NOT NULL,
                    INDEX idx_uuid_timestamp (uuid, timestamp),
                    INDEX idx_timestamp (timestamp)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """, historyTable));
        }
    }

    public CompletableFuture<Void> updateBalance(UUID uuid, String playerName, double balance) {
        return CompletableFuture.runAsync(() -> {
            if (!connected) {
                localCache.put(uuid, new PlayerBalance(uuid, playerName, balance, System.currentTimeMillis()));
                return;
            }

            String sql = String.format(
                    "INSERT INTO %s (uuid, player_name, balance, last_seen) VALUES (?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE player_name = ?, balance = ?, last_seen = ?",
                    getTableName("balances")
            );

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                long now = System.currentTimeMillis();
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setDouble(3, balance);
                ps.setLong(4, now);
                ps.setString(5, playerName);
                ps.setDouble(6, balance);
                ps.setLong(7, now);

                ps.executeUpdate();

                localCache.put(uuid, new PlayerBalance(uuid, playerName, balance, now));

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update balance for " + playerName, e);
                localCache.put(uuid, new PlayerBalance(uuid, playerName, balance, System.currentTimeMillis()));
            }
        });
    }

    public CompletableFuture<Map<UUID, PlayerBalance>> getTopBalances(int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, PlayerBalance> result = new LinkedHashMap<>();

            if (!connected) {
                localCache.values().stream()
                        .sorted((a, b) -> Double.compare(b.getBalance(), a.getBalance()))
                        .skip(offset)
                        .limit(limit)
                        .forEach(pb -> result.put(pb.getUuid(), pb));
                return result;
            }

            String sql = String.format(
                    "SELECT uuid, player_name, balance, last_seen FROM %s ORDER BY balance DESC LIMIT ? OFFSET ?",
                    getTableName("balances")
            );

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, limit);
                ps.setInt(2, offset);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    PlayerBalance pb = new PlayerBalance(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("player_name"),
                            rs.getDouble("balance"),
                            rs.getLong("last_seen")
                    );
                    result.put(pb.getUuid(), pb);
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to fetch top balances", e);
                return getTopBalancesFromCache(limit, offset);
            }

            return result;
        });
    }

    public Map<UUID, PlayerBalance> getTopBalancesSync(int limit, int offset) {
        Map<UUID, PlayerBalance> result = new LinkedHashMap<>();

        if (!connected) {
            localCache.values().stream()
                    .sorted((a, b) -> Double.compare(b.getBalance(), a.getBalance()))
                    .skip(offset)
                    .limit(limit)
                    .forEach(pb -> result.put(pb.getUuid(), pb));
            return result;
        }

        String sql = String.format(
                "SELECT uuid, player_name, balance, last_seen FROM %s ORDER BY balance DESC LIMIT ? OFFSET ?",
                getTableName("balances")
        );

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ps.setInt(2, offset);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                PlayerBalance pb = new PlayerBalance(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        rs.getDouble("balance"),
                        rs.getLong("last_seen")
                );
                result.put(pb.getUuid(), pb);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch top balances sync", e);
            return getTopBalancesFromCache(limit, offset);
        }

        return result;
    }

    public CompletableFuture<Optional<PlayerBalance>> getPlayerBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return Optional.ofNullable(localCache.get(uuid));
            }

            String sql = String.format(
                    "SELECT uuid, player_name, balance, last_seen FROM %s WHERE uuid = ?",
                    getTableName("balances")
            );

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    return Optional.of(new PlayerBalance(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("player_name"),
                            rs.getDouble("balance"),
                            rs.getLong("last_seen")
                    ));
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to fetch player balance", e);
            }

            return Optional.ofNullable(localCache.get(uuid));
        });
    }

    public Optional<PlayerBalance> getPlayerBalanceSync(UUID uuid) {
        if (!connected) {
            return Optional.ofNullable(localCache.get(uuid));
        }

        String sql = String.format(
                "SELECT uuid, player_name, balance, last_seen FROM %s WHERE uuid = ?",
                getTableName("balances")
        );

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(new PlayerBalance(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        rs.getDouble("balance"),
                        rs.getLong("last_seen")
                ));
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch player balance sync", e);
        }

        return Optional.ofNullable(localCache.get(uuid));
    }

    public CompletableFuture<Integer> getPlayerRank(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return getPlayerRankFromCache(uuid);
            }

            String sql = String.format(
                    "SELECT COUNT(*) + 1 as `rank` FROM %s WHERE balance > (SELECT balance FROM %s WHERE uuid = ?)",
                    getTableName("balances"), getTableName("balances")
            );

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    return rs.getInt("rank");
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get player rank", e);
            }

            return getPlayerRankFromCache(uuid);
        });
    }

    public int getPlayerRankSync(UUID uuid) {
        if (!connected) {
            return getPlayerRankFromCache(uuid);
        }

        String sql = String.format(
                "SELECT COUNT(*) + 1 as `rank` FROM %s WHERE balance > (SELECT balance FROM %s WHERE uuid = ?)",
                getTableName("balances"), getTableName("balances")
        );

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt("rank");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get player rank sync", e);
        }

        return getPlayerRankFromCache(uuid);
    }

    public CompletableFuture<Double> getServerAverage() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return localCache.values().stream()
                        .mapToDouble(PlayerBalance::getBalance)
                        .average()
                        .orElse(0.0);
            }

            String sql = String.format(
                    "SELECT AVG(balance) as average FROM %s",
                    getTableName("balances")
            );

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                if (rs.next()) {
                    return rs.getDouble("average");
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get server average", e);
            }

            return localCache.values().stream()
                    .mapToDouble(PlayerBalance::getBalance)
                    .average()
                    .orElse(0.0);
        });
    }

    public double getServerAverageSync() {
        if (!connected) {
            return localCache.values().stream()
                    .mapToDouble(PlayerBalance::getBalance)
                    .average()
                    .orElse(0.0);
        }

        String sql = String.format(
                "SELECT AVG(balance) as average FROM %s",
                getTableName("balances")
        );

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getDouble("average");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get server average sync", e);
        }

        return localCache.values().stream()
                .mapToDouble(PlayerBalance::getBalance)
                .average()
                .orElse(0.0);
    }

    public CompletableFuture<Map<UUID, PlayerBalance>> getActivePlayers(long minLastSeen) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, PlayerBalance> result = new HashMap<>();

            if (!connected) {
                localCache.forEach((uuid, pb) -> {
                    if (pb.getLastSeen() >= minLastSeen) {
                        result.put(uuid, pb);
                    }
                });
                return result;
            }

            String sql = String.format(
                    "SELECT uuid, player_name, balance, last_seen FROM %s WHERE last_seen >= ?",
                    getTableName("balances")
            );

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, minLastSeen);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    PlayerBalance pb = new PlayerBalance(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("player_name"),
                            rs.getDouble("balance"),
                            rs.getLong("last_seen")
                    );
                    result.put(pb.getUuid(), pb);
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get active players", e);
            }

            return result;
        });
    }

    public CompletableFuture<List<BalanceChange>> getTopEarners(int days, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<BalanceChange> result = new ArrayList<>();

            if (!connected) return result;

            long cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L);

            String sql = String.format("""
                SELECT h.uuid, b.player_name, SUM(h.change_amount) as total_earned 
                FROM %s h JOIN %s b ON h.uuid = b.uuid 
                WHERE h.timestamp >= ? AND h.change_amount > 0 
                GROUP BY h.uuid, b.player_name 
                ORDER BY total_earned DESC LIMIT ?
                """, getTableName("balance_history"), getTableName("balances"));

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, cutoff);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    result.add(new BalanceChange(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("player_name"),
                            rs.getDouble("total_earned")
                    ));
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get top earners", e);
            }

            return result;
        });
    }

    public CompletableFuture<Void> recordBalanceChange(UUID uuid, double oldBalance, double newBalance) {
        return CompletableFuture.runAsync(() -> {
            if (!connected) return;

            String sql = String.format(
                    "INSERT INTO %s (uuid, old_balance, new_balance, change_amount, timestamp) VALUES (?, ?, ?, ?, ?)",
                    getTableName("balance_history")
            );

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                ps.setDouble(2, oldBalance);
                ps.setDouble(3, newBalance);
                ps.setDouble(4, newBalance - oldBalance);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();

                cleanupOldHistory(conn);

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to record balance change", e);
            }
        });
    }

    private void cleanupOldHistory(Connection conn) throws SQLException {
        long cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        String sql = String.format("DELETE FROM %s WHERE timestamp < ?", getTableName("balance_history"));
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        }
    }

    private Map<UUID, PlayerBalance> getTopBalancesFromCache(int limit, int offset) {
        Map<UUID, PlayerBalance> result = new LinkedHashMap<>();
        localCache.values().stream()
                .sorted((a, b) -> Double.compare(b.getBalance(), a.getBalance()))
                .skip(offset)
                .limit(limit)
                .forEach(pb -> result.put(pb.getUuid(), pb));
        return result;
    }

    private int getPlayerRankFromCache(UUID uuid) {
        PlayerBalance target = localCache.get(uuid);
        if (target == null) return -1;

        return (int) localCache.values().stream()
                .filter(pb -> pb.getBalance() > target.getBalance())
                .count() + 1;
    }

    private String getTableName(String table) {
        return config.getTablePrefix() + table;
    }

    public boolean isConnected() {
        return connected;
    }

    public Map<UUID, PlayerBalance> getLocalCache() {
        return new HashMap<>(localCache);
    }

    public static class PlayerBalance {
        private final UUID uuid;
        private final String playerName;
        private final double balance;
        private final long lastSeen;

        public PlayerBalance(UUID uuid, String playerName, double balance, long lastSeen) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.balance = balance;
            this.lastSeen = lastSeen;
        }

        public UUID getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public double getBalance() { return balance; }
        public long getLastSeen() { return lastSeen; }
    }

    public static class BalanceChange {
        private final UUID uuid;
        private final String playerName;
        private final double amount;

        public BalanceChange(UUID uuid, String playerName, double amount) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.amount = amount;
        }

        public UUID getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public double getAmount() { return amount; }
    }
}