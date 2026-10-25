# BaseHunter (Fabric, Minecraft 26.1.2)

Client-side mod. Scans chunks as they load for player-made blocks, flags dense clusters as bases, and can loop
`/rtp` -> scan -> `/rtp` until it finds one.

## Build
Needs JDK 25.

    ./gradlew build

Jar: `build/libs/basehunter-1.0.0.jar`. Drop it in `mods/` with Fabric Loader 0.19.5+ and Fabric API for 26.1.2.

## Use
- **Right Shift** - config GUI. **Right Ctrl** - start/stop auto search (rebindable in Controls > BaseHunter).
- Base ESP: orange boxes on containers, red box + label on the whole cluster, tracer line from the bottom of the screen.
- Player ESP: cyan box, name, distance, tracer.
- On a find: 6-note alert sound + pulsing red screen flash + chat message with coordinates.
- Auto search: scans for `scanSeconds` after each teleport, then runs `/<rtpCommand>`, clicks the slot whose item name
  contains `firstClick` ("Overworld"), then the one containing `secondClick` ("NA East"), waits for the teleport, repeats.
  Menu names / command are in `config/basehunter.json`.

## Detection
Weights live in `BlockInfo.java`. A cluster is a base if, within `clusterRadius` blocks, it has `minChests`
containers (chest/trapped/barrel/ender/shulker) OR a total score >= `minScore`. Villages, bastions, ancient cities,
mansions etc. can trigger it - raise the thresholds if you get false positives.

## ESP notes
ESP is projected onto the 2D HUD (works through walls, no custom render pipeline). It assumes first person and uses
the FOV option, so it drifts a little while sprinting / with FOV effects, and is hidden in third person.
