# UUIDSwapper
A velocity plugin for swapping player UUIDs or usernames.

!!! NOT FINISHED !!!

## Finished features
- [x] Swap UUIDs and usernames
- [ ] Use online UUIDs (offline mode only)

## Use cases
- When switching from online to offline server mode (no need to transfer user data if it's a small server, you just specify username or offline UUID and set it to online UUID)
- Idk, your own purposes, the above one was the reason I made this

## TODO
- [ ] Use online skins (offline mode only)
- [ ] Swap skins
- [ ] Set default skin (custom skin pack?)
- [ ] Randomize usernames if needed (maybe skins too?)
- [ ] Toggle capes/skins (I wonder how capes work in properties)
- [ ] Command for changing username or UUID (might need to relog)

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
