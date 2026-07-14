package com.eventteams;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.util.Text;

/**
 * Holds the list of teams and a fast name -> team lookup. Player names are
 * normalized (case folded, whitespace collapsed) since RuneScape names are
 * case-insensitive and can come in with inconsistent spacing from pasted CSV.
 */
public class TeamRegistry
{
	private final Gson gson;
	private final Gson shareGson;

	private final List<TeamInfo> teams = new ArrayList<>();
	private final Map<String, TeamInfo> byNormalizedName = new LinkedHashMap<>();

	public TeamRegistry(Gson gson)
	{
		this.gson = gson;
		this.shareGson = gson.newBuilder().setPrettyPrinting().create();
	}

	public List<TeamInfo> getTeams()
	{
		return teams;
	}

	public void addTeam(TeamInfo team)
	{
		teams.add(team);
		reindex();
	}

	public void removeTeam(TeamInfo team)
	{
		teams.remove(team);
		reindex();
	}

	/** Replaces the whole roster, e.g. from a clipboard import. */
	public void setTeams(List<TeamInfo> newTeams)
	{
		teams.clear();
		teams.addAll(newTeams);
		reindex();
	}

	/** Call after mutating a team's name/color/roster in place. */
	public void reindex()
	{
		byNormalizedName.clear();
		for (TeamInfo team : teams)
		{
			for (String rawName : team.getPlayerNames())
			{
				String normalized = normalize(rawName);
				if (!normalized.isEmpty())
				{
					byNormalizedName.put(normalized, team);
				}
			}
		}
	}

	public TeamInfo getTeamForPlayer(String playerName)
	{
		if (playerName == null)
		{
			return null;
		}
		return byNormalizedName.get(normalize(playerName));
	}

	public Color getColorForPlayer(String playerName)
	{
		TeamInfo team = getTeamForPlayer(playerName);
		return team == null ? null : team.getColor();
	}

	public static String normalize(String name)
	{
		// Text.standardize/removeTags handles the <img=..> icon tags RuneScape
		// prefixes some names with (e.g. clan rank icons); collapse remaining
		// whitespace and lowercase so "Zezima", "zezima", "Zezima " all match.
		String cleaned = Text.removeTags(name).replace('\u00A0', ' ').trim();
		return cleaned.toLowerCase();
	}

	public static List<String> parseCsv(String csv)
	{
		List<String> names = new ArrayList<>();
		if (csv == null)
		{
			return names;
		}
		for (String part : csv.split("[,\\r\\n]+"))
		{
			String trimmed = part.trim();
			if (!trimmed.isEmpty())
			{
				names.add(trimmed);
			}
		}
		return names;
	}

	// --- persistence -------------------------------------------------------

	private static class StoredTeam
	{
		String name;
		int colorRgb;
		List<String> playerNames;
	}

	public String serialize()
	{
		List<StoredTeam> stored = new ArrayList<>();
		for (TeamInfo team : teams)
		{
			StoredTeam s = new StoredTeam();
			s.name = team.getName();
			s.colorRgb = team.getColor().getRGB();
			s.playerNames = team.getPlayerNames();
			stored.add(s);
		}
		return gson.toJson(stored);
	}

	/** Replaces this registry's teams with the contents of a stored config JSON blob. */
	public void loadFromJson(String json)
	{
		teams.clear();
		if (json == null || json.isEmpty())
		{
			reindex();
			return;
		}

		Type listType = new TypeToken<List<StoredTeam>>()
		{
		}.getType();

		List<StoredTeam> stored = gson.fromJson(json, listType);
		if (stored != null)
		{
			for (StoredTeam s : stored)
			{
				TeamInfo team = new TeamInfo(s.name, new Color(s.colorRgb, true));
				team.setPlayerNames(s.playerNames == null ? new ArrayList<>() : s.playerNames);
				teams.add(team);
			}
		}
		reindex();
	}

	// --- clipboard share format ---------------------------------------------
	// Human-editable, unlike the internal storage blob:
	// [{"name": "Team A", "color": "#E74C3C", "players": ["Zezima", "B0aty"]}]

	private static class ShareTeam
	{
		String name;
		String color;
		List<String> players;
	}

	public String toShareJson()
	{
		List<ShareTeam> out = new ArrayList<>();
		for (TeamInfo team : teams)
		{
			ShareTeam s = new ShareTeam();
			s.name = team.getName();
			s.color = String.format("#%06X", team.getColor().getRGB() & 0xFFFFFF);
			s.players = new ArrayList<>(team.getPlayerNames());
			out.add(s);
		}
		return shareGson.toJson(out);
	}

	/** Parses the share format leniently; returns null only if the JSON is unusable. */
	public List<TeamInfo> fromShareJson(String json)
	{
		List<ShareTeam> stored;
		try
		{
			Type listType = new TypeToken<List<ShareTeam>>()
			{
			}.getType();
			stored = shareGson.fromJson(json, listType);
		}
		catch (JsonSyntaxException ex)
		{
			return null;
		}

		if (stored == null)
		{
			return null;
		}

		List<TeamInfo> result = new ArrayList<>();
		int index = 1;
		for (ShareTeam s : stored)
		{
			if (s == null)
			{
				continue;
			}

			Color color = Color.WHITE;
			if (s.color != null)
			{
				try
				{
					color = Color.decode(s.color.trim());
				}
				catch (NumberFormatException ignored)
				{
				}
			}

			TeamInfo team = new TeamInfo(
				s.name == null || s.name.trim().isEmpty() ? "Team " + index : s.name.trim(), color);
			if (s.players != null)
			{
				for (String player : s.players)
				{
					if (player != null && !player.trim().isEmpty())
					{
						team.getPlayerNames().add(player.trim());
					}
				}
			}
			result.add(team);
			index++;
		}
		return result;
	}
}
