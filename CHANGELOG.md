# Changelog

## 1.1.0

* Mod list page: Crop Harvester icon and logo, website link to CurseForge.
* In-game settings: the "Config" button in the mod list opens the settings; changes apply right away,
  also in a running world (on a server, the server's fertilizer list is used).
* Simpler, safer config: only non-critical settings remain, with strict ranges (energy per check and per harvest,
  harvesting options, IC2 nutrients and hydration, fertilizer list, zone highlight). Area, scan speed, base power,
  storage and tank sizes, augment chances and bonuses are now fixed. Old entries are removed from the config file
  automatically. Fertilizer values outside 2–8 (strength) and 1–1024 (units) are clamped with a warning in the log.
* Only the fertilizer list is synced from the server. Client and server must both run 1.1.0.
* Performance: transparent side overlays are no longer rendered, the area highlight skips far machines,
  fertilizer is not pulled in when the storage is full.

## 1.0.0 — first public release

* Crop Harvester: a Thermal Expansion–style machine that harvests and replants crops on farmland in three layers
  (one block below, level with and one block above the machine), from any mod; pumpkins and melons from stems.
* Five levels via TE upgrade kits: area 3×3 to 11×11, scan speed 1 to 16 columns per second.
* Fertilizer storage (up to 1000 growth cycles); fertilizers of different strength blend into an average strength.
  Storage and energy are kept in the item when the machine is dismantled.
* Augments: Harvest Area Extender, Irrigation Module (water tank, keeps all farmland in the area wet),
  Fertilizer Recycler, Growth Catalyst.
* Sleep mode: no energy is spent when there is nothing to do; the front panel shows the machine state
  (lamps, harvesting window, fertilizer slot, orange frame when the output storage is full).
* IC2 crop sticks: harvesting, weeding, nutrients and watering.
* JEI/HEI fertilizer page, Hwyla tooltip, server-to-client config sync, owner protection, redstone control,
  side configuration with auto-input and auto-output.
* English and Russian localization.
