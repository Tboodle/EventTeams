# Event Teams

A RuneLite plugin for organizing clan event teams (e.g. bingo). Highlights each
team's players in the game world, on the minimap, and in chat, and shows which
tracked players are currently online (with their world) based on your in-game
Clan's online-member channel.

## Setting up teams

Teams are managed from the plugin's side panel, not the config screen. Open the
Event Teams panel from the RuneLite sidebar to:

- **Add a team**, then give it a name and a color.
- **Add players** one at a time, or import a whole setup at once.
- See **who's online** per team, with their current world.
- **Export / Import teams JSON** to share a roster with other clan officers via
  the clipboard.

The share format is human-editable:

```json
[{ "name": "Team A", "color": "#E74C3C", "players": ["Zezima", "B0aty"] }]
```

<!-- TODO: screenshot of the side panel with a couple of teams set up -->
![Event Teams side panel](docs/images/side-panel.png)

## Configuration

Each option below is independent and can be toggled in the Event Teams config
section.

---

### Highlight players in world

Draws a team-colored name above each tracked player in the game world. Your own
name is never labeled. Only players currently loaded in your scene are drawn.

**Default:** on

<!-- TODO: screenshot of this option in the config panel -->
![Config — Highlight players in world](docs/images/config-highlight-in-world.png)

<!-- TODO: screenshot of an in-world player with a team-colored name overhead -->
![Enabled — Highlight players in world](docs/images/enabled-highlight-in-world.png)

---

### Highlight on minimap

Draws a team-colored dot on the minimap for each tracked player, painting over
the game's native dot (e.g. the green friend dot). As with the in-world names,
this only covers players currently loaded in your scene.

**Default:** on

<!-- TODO: screenshot of this option in the config panel -->
![Config — Highlight on minimap](docs/images/config-highlight-minimap.png)

<!-- TODO: screenshot of the minimap showing team-colored dots -->
![Enabled — Highlight on minimap](docs/images/enabled-highlight-minimap.png)

---

### Show names on minimap

Additionally draws each tracked player's name in their team color just above
their minimap dot. Requires **Highlight on minimap** to be on. Off by default —
minimap names get cluttered quickly when several teammates are close together.

**Default:** off

<!-- TODO: screenshot of this option in the config panel -->
![Config — Show names on minimap](docs/images/config-minimap-names.png)

<!-- TODO: screenshot of the minimap with names next to the dots -->
![Enabled — Show names on minimap](docs/images/enabled-minimap-names.png)

---

### Color names in chat

Colors a tracked player's name in their team color in public and clan chat.
Everything else on the line is left alone — including your clan tag — and rank
icons are preserved. Private messages and non-team players are never touched.

**Default:** on

<!-- TODO: screenshot of this option in the config panel -->
![Config — Color names in chat](docs/images/config-chat-colors.png)

<!-- TODO: screenshot of public + clan chat lines with team-colored names -->
![Enabled — Color names in chat](docs/images/enabled-chat-colors.png)

---

### Show team name in chat

Additionally shows the player's team name as a `[Team]` tag before their name.
Requires **Color names in chat** to be on.

- **Public chat:** `[Team] Player: message`
- **Clan chat:** `[YourClan] [Team] Player: message` — the team tag slots in
  right after your clan tag, leaving the clan tag and rank icons intact.
- **Broadcasts** (drops, PBs): `[Team] Player received a drop: ...`

Only the team name inside the brackets is colored; the brackets themselves stay
the default text color.

**Default:** off

<!-- TODO: screenshot of this option in the config panel -->
![Config — Show team name in chat](docs/images/config-team-name-chat.png)

<!-- TODO: screenshot of a clan chat line showing [Clan] [Team] Player -->
![Enabled — Show team name in chat](docs/images/enabled-team-name-chat.png)

---

### Team square in friends list

Draws a small team-colored square immediately after a tracked player's name in
the in-game friends list, leaving the name itself in its normal color.

If the game is already showing a "recently renamed" icon next to that player, it
is shifted right so the two don't overlap.

**Default:** on

<!-- TODO: screenshot of this option in the config panel -->
![Config — Team square in friends list](docs/images/config-friends-list.png)

<!-- TODO: screenshot of the friends list with team squares after names -->
![Enabled — Team square in friends list](docs/images/enabled-friends-list.png)

---

## Known limitations / design notes

- **Name matching is exact-ish, normalized for case/whitespace.** If someone
  changes their RSN mid-competition, you'll need to update their team's roster.
- **In-world and minimap highlighting only covers players loaded in your scene**,
  so distant teammates won't be drawn even though they're on a roster.
- **Chat tagging only touches public/clan chat**, not private messages, to
  avoid rewriting DMs.
- **The friends list squares are only drawn when the list updates.** Editing a
  team's color or roster while the friends list is already open won't re-draw the
  squares until the list next refreshes (e.g. a friend logs in/out).
- **Everything is local.** The plugin talks to no external service; rosters live
  in your RuneLite config.
