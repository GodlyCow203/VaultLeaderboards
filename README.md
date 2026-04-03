# VaultLeaderboards

A powerful Spigot/Paper plugin that creates economy leaderboards with PlaceholderAPI support, FastStats integration, and real-time updates.

## Features

✨ **Economy Leaderboards**
- Display top player balances with PlaceholderAPI
- Server average balance calculation
- Per-player rank information

⏰ **Time-Period Leaderboards**
- Daily top earners (resets every day)
- Weekly top earners (resets every week)
- Monthly top earners (resets every month)

🌐 **FastStats Integration**
- Real-time data from FastStats.dev
- Automatic periodic syncing
- Cloud-based leaderboards

🗄️ **Cross-Server Support**
- MySQL/MariaDB database synchronization
- Multi-server leaderboard sharing
- Configurable sync intervals

🎨 **PlaceholderAPI Integration**
- Easy-to-use placeholders for scoreboards, chat, signs, etc.
- Full MiniMessage color support
- Customizable display formats

## Installation

1. Download the latest `.jar` from [Releases](https://github.com/GodlyCow203/VaultLeaderboards/releases)
2. Place it in your `plugins/` folder
3. Restart your server
4. Configure `plugins/VaultLeaderboards/config.yml`

## Requirements

- **Vault** (required) - For economy support
- **PlaceholderAPI** (required) - For placeholder expansion
- **Paper/Spigot 1.20+** - Server software
- **Java 21+** - Runtime environment

## Placeholders

### General Leaderboards
```
%vaultleaderboards_top_1%     - 1st top balancer
%vaultleaderboards_top_5%     - 5th top balancer
%vaultleaderboards_server_average% - Server average balance
```

### Time-Period Leaderboards (FastStats)
```
%vaultleaderboards_daily_1%   - Top daily earner
%vaultleaderboards_daily_5%   - 5th daily earner

%vaultleaderboards_weekly_1%  - Top weekly earner
%vaultleaderboards_weekly_3%  - 3rd weekly earner

%vaultleaderboards_monthly_1% - Top monthly earner
%vaultleaderboards_monthly_2% - 2nd monthly earner
```

### Player Specific
```
%vaultleaderboards_playername% - Player rank and balance
%vaultleaderboards_topearner_1_7d% - 1st earner last 7 days
```

## Configuration

### Basic Setup
```yaml
database:
  enabled: false  # Set to true for cross-server support
  host: localhost
  port: 3306
  database: vaultleaderboards
  username: root
  password: "minecraft"
```

### Time Period Reset Times
```yaml
time-periods:
  daily:
    reset-time: "00:00"    # Midnight UTC
    timezone: "UTC"
  
  weekly:
    reset-day: "MONDAY"
    reset-time: "00:00"
  
  monthly:
    reset-day: 1           # First day of month
    reset-time: "00:00"
```

### Customize Placeholder Formats
```yaml
placeholders:
  top: "<yellow><rank>. <green><player></green> - <aqua>$<balance>"
  daily-top: "<yellow><rank>. <green><player></green> <aqua>+$<amount></aqua> (today)"
  weekly-top: "<yellow><rank>. <green><player></green> <aqua>+$<amount></aqua> (week)"
  monthly-top: "<yellow><rank>. <green><player></green> <aqua>+$<amount></aqua> (month)"
```

## Commands

```
/vaultleaderboards help        - Show help message
/vaultleaderboards reload      - Reload configuration
/vaultleaderboards version     - Check for updates
/vaultleaderboards status      - Database connection status
/vaultleaderboards sync        - Force database synchronization
```

## Permissions

- `vaultleaderboards.help` - View help command
- `vaultleaderboards.reload` - Reload configuration
- `vaultleaderboards.version` - Check version
- `vaultleaderboards.*` - Full access (admin)

## How to Use with Scoreboards

Add to your scoreboard plugin config:
```
%vaultleaderboards_top_1%
%vaultleaderboards_top_2%
%vaultleaderboards_top_3%
%vaultleaderboards_server_average%
```

## Database Setup (Optional)

For cross-server support, create a database:
```sql
CREATE DATABASE vaultleaderboards;
```

Then enable in config.yml and restart your server.

## Support for Scoreboards

Works with:
- ✅ TAB
- ✅ FeatherBoard
- ✅ Animated Tab
- ✅ Any scoreboard plugin that supports PlaceholderAPI

## Building from Source

```bash
git clone https://github.com/GodlyCow203/VaultLeaderboards.git
cd VaultLeaderboards
mvn clean package
```

The compiled JAR will be in `target/VaultLeaderboards-*.jar`

## License

This project is licensed under the MIT License - see LICENSE file for details.

## Author

- **_GodlyCow** - Main Developer

## Support & Issues

Found a bug? Have a feature request? Open an issue on [GitHub Issues](https://github.com/GodlyCow203/VaultLeaderboards/issues)

## Contributing

Contributions are welcome! Feel free to submit pull requests.

---

**Version:** 1.6  
**Last Updated:** 2026  
**Java:** 21+  
**Spigot/Paper:** 1.20+
