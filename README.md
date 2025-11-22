# UUIDSwapper
A velocity plugin for swapping player UUIDs or usernames.

!!! NOT FINISHED !!!

Version v1.0.0 was just quickly released because I needed it for my server so it has some unused configuration and maybe even code.

## Finished features
- [x] Swap UUIDs and usernames
- [ ] Use online UUIDs

## Use cases (as of v1.0.0)
- When switching from online to offline server mode (no need to transfer user data if it's a small server, you just specify username or offline UUID and set it to online UUID)

## TODO / Currently working on (Unreleased)
- [x] Online UUIDs
- [x] Multiple API services for getting online UUID
- [ ] Online UUID caching (**SQLite**/**Memory**/~~JSON~~) **(almost done)**
  - [x] Automatically download database drivers
- [ ] Cross-platform support (BungeeCord, Velocity, Paper/Folia, ~~Spigot~~ (not supported)))
- Skins
  - [x] Use online skins
  - [ ] Swap skins
  - [ ] ~~Set default skin (custom skin pack?)~~ (Not possible)
- [ ] Randomize usernames/UUIDs **(almost done)**
- [ ] ~~Toggle capes/skins~~ (Not possible)
- [x] Command for changing username or UUID

## Default configuration:
Configuration is TOML based and resides in `/plugins/uuid-swapper/config.toml`
```toml
# UUID Swapper by ItsTauTvyDas

# Only works when online mode is set to false
always-use-online-uuids = false

# Set to true if you still want to swap UUIDs even when always-use-online-uuids is enabled
swap-uuids = true

# UUID swapping
# If you want to use usernames, add u: prefix
[swapped-uuids]
#"u:ItsTauTvyDas" = "96642c39-6de2-3b20-a133-b354dcc36016"
#"cfe17913-ebc3-3cfb-9162-99908590f8f2" = "aacb3ea4-8b3d-3830-8dc0-11a765a0de3a"

# Set custom player names
# If you use UUID that has been previously swapped, use here the original
# For usernames, add u: prefix
[custom-player-names]
#"96642c39-6de2-3b20-a133-b354dcc36016" = "Herobrine"
#"u:Steve" = "Steve1"
```

## [Future WIKI](https://github.com/ItsTauTvyDas/UUIDSwapper/wiki)

## Future configuration
```json
{
  "database": {
    "enabled": false,
    "download-driver": true,
    "driver": "SQLite",
    "file": "players-data.db",
    "timeout": 5000,
    "keep-open-time": 10,
    "timer-repeat-time": 1,
    "debug": false
  },
  "online-authentication": {
    "enabled": false,
    "use-service": "PlayerDB",
    "fallback-services": [
      "MinecraftServices"
    ],
    "fallback-service-remember-time": 21600,
    "max-timeout": 6000,
    "min-timeout": 1000,
    "check-for-online-uuid": true,
    "send-messages-to-console": true,
    "send-error-messages-to-console": true,
    "service-connection-throttle": 5000,
    "service-connection-throttled-message": "UUID service connection throttles, wait {time-left} seconds until you can connect again!",
    "caching": {
      "enabled": true,
      "keep-time": 7200,
      "use-created-at": true,
      "use-updated-at": true
    },
    "username-changes": {
      "check-depending-on-ip-address": false,
      "check-username-cache": true
    },
    "service-defaults": {
      "expect-status-code": 200,
      "bad-uuid-disconnect-message": "&cFailed to get your online UUID (bad UUID), contact server's administrator!",
      "default-disconnect-message": "&cFailed to get your online UUID",
      "connection-error-disconnect-message": "&cFailed to get your online UUID (connection error), contact server's administrator!",
      "service-bad-status-disconnect-message": "&cFailed to get your online UUID (service returned {http.status}), contact server's administrator!",
      "unknown-error-disconnect-message": "&cFailed to get your online UUID (unknown error), contact server's administrator!",
      "service-timeout-disconnect-message": "&cFailed to get your online UUID (service timed out), try again later!",
      "timeout": 3000,
      "allow-caching": true,
      "use-fallbacks": [
        "ON_CONNECTION_ERROR",
        "ON_INVALID_UUID",
        "ON_BAD_UUID_PATH",
        "ON_UNKNOWN_ERROR",
        "ON_BAD_STATUS"
      ],
      "headers": {
        "Accept": "application/json"
      }
    },
    "services": [
      {
        "name": "PlayerDB",
        "endpoint": "https://playerdb.co/api/player/minecraft/{username}",
        "json-path-to-uuid": "data.player.id",
        "json-path-to-properties": "data.player.properties",
        "ignore-status-code": true,
        "response-handlers": [
          {
            "state": "AFTER_UUID",
            "allow-player-to-join": true,
            "conditions": {
              "response.code": "minecraft.invalid_username"
            }
          }
        ]
      },
      {
        "name": "MinecraftServices",
        "endpoint": "https://api.minecraftservices.com/minecraft/profile/lookup/name/{username}",
        "json-path-to-uuid": "id",
        "ignore-status-code": true,
        "response-handlers": [
          {
            "state": "AFTER_UUID",
            "allow-player-to-join": true,
            "conditions": {
              "::response.errorMessage": true
            }
          }
        ]
      }
    ]
  },
  "player-randomizer": {
    "enabled": false,
    "username": {
      "randomize": true,
      "save": false,
      "characters": "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM123456789_"
    },
    "uuid": {
      "randomize": true,
      "save": false
    }
  },
  "swapped-uuids": {
    "u:Notch": "00000000-0000-0000-0000-000000000000",
    "00000000-0000-0000-0000-000000000000": "00000000-0000-0000-0000-000000000001"
  },
  "custom-player-names": {
    "u:Notch": "Herobrine",
    "00000000-0000-0000-0000-000000000000": "jeb_"
  }
}
```
