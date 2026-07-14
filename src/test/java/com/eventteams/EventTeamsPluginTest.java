package com.eventteams;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class EventTeamsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(EventTeamsPlugin.class);
		RuneLite.main(args);
	}
}
