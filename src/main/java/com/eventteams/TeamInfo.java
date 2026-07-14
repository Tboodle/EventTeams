package com.eventteams;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * One bingo team: a display name, a color, and the CSV roster of player names
 * as the user typed them (kept around so the panel can re-render the raw list
 * for editing without lossy round-tripping through the normalized lookup map).
 */
public class TeamInfo
{
	private String name;
	private Color color;
	private List<String> playerNames = new ArrayList<>();

	public TeamInfo(String name, Color color)
	{
		this.name = name;
		this.color = color;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public Color getColor()
	{
		return color;
	}

	public void setColor(Color color)
	{
		this.color = color;
	}

	public List<String> getPlayerNames()
	{
		return playerNames;
	}

	public void setPlayerNames(List<String> playerNames)
	{
		this.playerNames = playerNames;
	}
}
