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
[
  {
    "name": "Team 1",
    "color": "#F255F2",
    "players": [
      "Zezima",
      "B0aty"
    ]
  },
  {
    "name": "Team 2",
    "color": "#55F2D9",
    "players": [
      "Tboodle",
      "Odablock"
    ]
  }
]
```

<!-- TODO: screenshot of the side panel with a couple of teams set up -->
<img width="232" height="640" alt="image" src="https://github.com/user-attachments/assets/0162ddb3-d2e1-4d35-b01d-88d41e4ef266" />


## Configuration

Each option below is independent and can be toggled in the Event Teams config
section.

<img width="231" height="304" alt="image" src="https://github.com/user-attachments/assets/e3f7a71d-9bd9-4ba5-8a34-f9011b4d3ac3" />


---

### Highlight players in world

Draws a team-colored name above each tracked player in the game world. Your own
name is never labeled. Only players currently loaded in your scene are drawn.

**Default:** on

<img width="229" height="25" alt="image" src="https://github.com/user-attachments/assets/46e59aa1-3291-4923-b57a-8b8f93891d15" />


<img width="861" height="462" alt="image" src="https://github.com/user-attachments/assets/7be10f60-5691-4fea-b261-7ad00025cc95" />

---

### Highlight on minimap

Draws a team-colored dot on the minimap for each tracked player, painting over
the game's native dot (e.g. the green friend dot). As with the in-world names,
this only covers players currently loaded in your scene.

**Default:** on

<img width="235" height="34" alt="image" src="https://github.com/user-attachments/assets/427bafa3-8d33-40a2-97db-02ff2b4136be" />


<!-- TODO: screenshot of the minimap showing team-colored dots -->
![Enabled — Highlight on minimap](docs/images/enabled-highlight-minimap.png)

---

### Show names on minimap

Additionally draws each tracked player's name in their team color just above
their minimap dot. Requires **Highlight on minimap** to be on. Off by default —
minimap names get cluttered quickly when several teammates are close together.

**Default:** off

<img width="233" height="28" alt="image" src="https://github.com/user-attachments/assets/711130fd-6082-47a8-a894-3bb8ba774c7f" />


<img width="351" height="250" alt="image" src="https://github.com/user-attachments/assets/cba9a5c0-855a-4004-808a-8d7e153e891b" />

---

### Color names in chat

Colors a tracked player's name in their team color in public and clan chat.
Everything else on the line is left alone — including your clan tag — and rank
icons are preserved. Private messages and non-team players are never touched.

**Default:** on

<img width="233" height="30" alt="image" src="https://github.com/user-attachments/assets/241af9ae-4424-4837-b043-95dfa79f963e" />


<img width="680" height="43" alt="image" src="https://github.com/user-attachments/assets/7923c789-9011-4612-b755-5a4e9d0a8427" />

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

<img width="231" height="24" alt="image" src="https://github.com/user-attachments/assets/c6742cae-e0e9-4b28-b43f-1f85698eef8f" />


<img width="769" height="70" alt="image" src="https://github.com/user-attachments/assets/334d3867-ab7f-4088-91b4-5d53f25a6b77" />

---

### Team square in friends list

Draws a small team-colored square immediately after a tracked player's name in
the in-game friends list, leaving the name itself in its normal color.

If the game is already showing a "recently renamed" icon next to that player, it
is shifted right so the two don't overlap.

**Default:** on

<img width="227" height="26" alt="image" src="https://github.com/user-attachments/assets/e06a8c9b-87d7-4ed8-a5bf-4b7370c2266c" />


<img width="360" height="499" alt="image" src="https://github.com/user-attachments/assets/e2668cfd-bf0f-457b-a9e0-688f9b97c70a" />

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
