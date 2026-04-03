package net.godlycow.org.vaultleaderboards.faststats;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class FastStatsManager {
    private final JavaPlugin plugin;
    private final String projectId;
    private final String apiBaseUrl = "https://api.faststats.dev/web";
    private final OkHttpClient httpClient;
    private final Gson gson;
    private boolean enabled;

    public FastStatsManager(JavaPlugin plugin, String projectId) {
        this.plugin = plugin;
        this.projectId = projectId;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        this.enabled = !projectId.isEmpty();

        if (enabled) {
            plugin.getLogger().info("FastStats integration enabled with project ID: " + projectId);
        }
    }

    public CompletableFuture<List<FastStatsEarner>> getTopEarners(String timePeriod, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            if (!enabled) {
                return Collections.emptyList();
            }

            try {
                String url = String.format("%s/projects/%s/leaderboard?period=%s&limit=%d",
                        apiBaseUrl, projectId, timePeriod, limit);

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        return parseTopEarners(responseBody);
                    } else {
                        plugin.getLogger().warning("FastStats API returned status: " + response.code());
                        return Collections.emptyList();
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to fetch FastStats data: ", e);
                return Collections.emptyList();
            }
        });
    }
    public CompletableFuture<Optional<FastStatsEarner>> getPlayerPeriodStats(String playerName, String timePeriod) {
        return CompletableFuture.supplyAsync(() -> {
            if (!enabled) {
                return Optional.empty();
            }

            try {
                String url = String.format("%s/projects/%s/player/%s/stats?period=%s",
                        apiBaseUrl, projectId, playerName, timePeriod);

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                        if (json.has("data")) {
                            JsonObject data = json.getAsJsonObject("data");
                            String name = data.get("playerName").getAsString();
                            double amount = data.get("amount").getAsDouble();
                            return Optional.of(new FastStatsEarner(name, amount));
                        }
                    }
                    return Optional.empty();
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to fetch FastStats player data: ", e);
                return Optional.empty();
            }
        });
    }

    private List<FastStatsEarner> parseTopEarners(String responseBody) {
        try {
            List<FastStatsEarner> earners = new ArrayList<>();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            if (json.has("data")) {
                JsonArray dataArray = json.getAsJsonArray("data");
                for (JsonElement element : dataArray) {
                    if (element.isJsonObject()) {
                        JsonObject obj = element.getAsJsonObject();
                        String name = obj.has("playerName") ? obj.get("playerName").getAsString() : "Unknown";
                        double amount = obj.has("amount") ? obj.get("amount").getAsDouble() : 0.0;
                        earners.add(new FastStatsEarner(name, amount));
                    }
                }
            }

            return earners;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse FastStats response: ", e);
            return Collections.emptyList();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        httpClient.connectionPool().evictAll();
    }

    public static class FastStatsEarner {
        private final String playerName;
        private final double amount;

        public FastStatsEarner(String playerName, double amount) {
            this.playerName = playerName;
            this.amount = amount;
        }

        public String getPlayerName() {
            return playerName;
        }

        public double getAmount() {
            return amount;
        }
    }
}
