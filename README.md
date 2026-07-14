# Event Teams

A RuneLite plugin for organizing clan event teams (e.g. bingo). Highlights each
team's players in the game world, on the minimap, and in chat, and shows which
tracked players are currently online (with their world) based on your in-game
Clan's online-member channel.

## Layout

- `EventTeamsPlugin.java` — wiring: config, overlay registration, clan-channel
  polling for online status, chat message tagging.
- `EventTeamsConfig.java` — persisted settings (team data blob + display toggles).
- `TeamRegistry.java` — team list + normalized name → team lookup, JSON
  serialize/deserialize for storage in `ConfigManager`.
- `TeamInfo.java` — one team's name, color, and roster.
- `EventTeamsOverlay.java` — draws team-colored names in-world/minimap.
- `EventTeamsPanel.java` — sidebar UI: add/edit teams, add players one at a
  time or paste a JSON array of names, per-player online dots (with world),
  and copy/paste of the full team setup as shareable JSON.

## Known limitations / design notes

- **Name matching is exact-ish, normalized for case/whitespace.** If someone
  changes their RSN mid-competition, you'll need to update their team's roster.
- **Chat tagging only touches public/clan chat**, not private messages, to
  avoid rewriting DMs.
