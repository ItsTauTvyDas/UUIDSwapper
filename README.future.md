# UUIDSwapper
A multi-platform plugin for swapping player UUIDs/usernames or even use online mode on offline/insecure server!

Since I wrote a lot of wiki already before the actual README, just go [there](https://github.com/ItsTauTvyDas/UUIDSwapper/wiki).

## ⭐ Main Features
More details about the features can be found in the [configuration wiki](https://github.com/ItsTauTvyDas/UUIDSwapper/wiki/Configuration)!

* 🔑 Online authentication - authenticates players based on their username on offline/insecure server.
  * Extensive service configuration (for getting online UUID and properties)
  * [Response handlers](https://github.com/ItsTauTvyDas/UUIDSwapper/wiki/Configuration#response-handler)
* 🧙‍♂️ Randomizer - make player usernames and UUIDs random on each join or save it to make them persistent.
* 💽 Databases - built-in SQLite, JSON and Memory database drivers. Memory simply saves information inside the plugin, so the data is not persistent!
* ⌨️ Manual UUID/username swapper - define which uuids and usernames must be swapped.

## 📒 Some notes

* This whole project is such an overkill, made an annotation processor for just generating whole ahh configuration wiki, multi-platform stuff..
* If you are going to fork this and modify in your way, all I can say is good luck, the gradle build script is held together with hopes and dreams :3

## 📝 TODO?

* Make a proper multi-module project, gradle got too complicated already for multi-platforming because I wanted to support different java versions
* Auto generate configuration (the problem with this is GSON, I would need to write a custom adapter for it)
* Support Spigot? Not really possible I feel like without using packets, I don't want to reinvent SkinsRestorer duh.