# Event Teams

A RuneLite plugin for organizing clan event teams (e.g. bingo). Highlights each
team's players in the game world, on the minimap, and in chat, and shows which
tracked players are currently online (with their world) based on your in-game
Clan's online-member channel.

## Known limitations / design notes

- **Name matching is exact-ish, normalized for case/whitespace.** If someone
  changes their RSN mid-competition, you'll need to update their team's roster.
- **Chat tagging only touches public/clan chat**, not private messages, to
  avoid rewriting DMs.
