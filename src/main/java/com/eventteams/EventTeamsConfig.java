package com.eventteams;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("eventteams")
public interface EventTeamsConfig extends Config
{
	// Teams are edited through the side panel, not this config screen — this
	// key just persists the serialized roster between client restarts.
	// Hidden from the normal config GUI since it's raw JSON, not user-facing.
	@ConfigItem(
		keyName = "teamData",
		name = "",
		description = "",
		hidden = true
	)
	default String teamData()
	{
		return "";
	}

	@ConfigItem(
		keyName = "highlightInWorld",
		name = "Highlight players in world",
		description = "Draw team-colored names above players in the game world",
		position = 1
	)
	default boolean highlightInWorld()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightOnMinimap",
		name = "Highlight on minimap",
		description = "Draw a team-colored dot on the minimap for each tracked player",
		position = 2
	)
	default boolean highlightOnMinimap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNamesOnMinimap",
		name = "Show names on minimap",
		description = "Also draw the player's name next to their minimap dot",
		position = 3
	)
	default boolean showNamesOnMinimap()
	{
		return false;
	}

	@ConfigItem(
		keyName = "tagChatMessages",
		name = "Color names in chat",
		description = "Color a tracked player's name in their team color in public and clan chat",
		position = 4
	)
	default boolean tagChatMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTeamNameInChat",
		name = "Show team name in chat",
		description = "Also prefix the player's name with their team name (after the clan tag, before the name)",
		position = 5
	)
	default boolean showTeamNameInChat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightFriendsList",
		name = "Team square in friends list",
		description = "Draw a small team-colored square next to tracked players in the in-game friends list",
		position = 6
	)
	default boolean highlightFriendsList()
	{
		return true;
	}
}
