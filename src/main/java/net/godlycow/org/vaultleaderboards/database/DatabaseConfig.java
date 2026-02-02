package net.godlycow.org.vaultleaderboards.database;

import net.godlycow.org.vaultleaderboards.VaultLeaderboards;
import org.bukkit.configuration.ConfigurationSection;

public class DatabaseConfig {
    private final VaultLeaderboards plugin;

    private boolean enabled;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String tablePrefix;
    private int maxConnections;
    private long connectionTimeout;
    private boolean ssl;
    private boolean autoReconnect;

    public DatabaseConfig(VaultLeaderboards plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        ConfigurationSection db = plugin.getConfig().getConfigurationSection("database");
        if (db == null) {
            setDefaults();
            return;
        }

        this.enabled = db.getBoolean("enabled", false);
        this.host = db.getString("host", "localhost");
        this.port = db.getInt("port", 3306);
        this.database = db.getString("database", "vaultleaderboards");
        this.username = db.getString("username", "root");
        this.password = db.getString("password", "");
        this.tablePrefix = db.getString("table-prefix", "vl_");
        this.maxConnections = db.getInt("max-connections", 10);
        this.connectionTimeout = db.getLong("connection-timeout", 5000);
        this.ssl = db.getBoolean("ssl", false);
        this.autoReconnect = db.getBoolean("auto-reconnect", true);
    }

    private void setDefaults() {
        this.enabled = false;
        this.host = "localhost";
        this.port = 3306;
        this.database = "vaultleaderboards";
        this.username = "root";
        this.password = "";
        this.tablePrefix = "vl_";
        this.maxConnections = 10;
        this.connectionTimeout = 5000;
        this.ssl = false;
        this.autoReconnect = true;
    }

    public boolean isEnabled() { return enabled; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getTablePrefix() { return tablePrefix; }
    public int getMaxConnections() { return maxConnections; }
    public long getConnectionTimeout() { return connectionTimeout; }
    public boolean useSsl() { return ssl; }
    public boolean autoReconnect() { return autoReconnect; }

    public String getJdbcUrl() {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=%b&autoReconnect=%b&connectTimeout=%d",
                host, port, database, ssl, autoReconnect, connectionTimeout);
    }
}