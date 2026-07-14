package com.eventteams;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Event Teams",
	description = "Highlights clan event teams in-world and in chat, and tracks online status per team",
	tags = {"bingo", "event", "clan", "teams"}
)
public class EventTeamsPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "eventteams";
	private static final String CONFIG_KEY_TEAM_DATA = "teamData";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventTeamsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatIconManager chatIconManager;

	private TeamRegistry registry;
	private EventTeamsOverlay overlay;
	private EventTeamsMinimapOverlay minimapOverlay;
	private EventTeamsPanel panel;
	private NavigationButton navButton;

	@Provides
	EventTeamsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(EventTeamsConfig.class);
	}

	@Override
	protected void startUp()
	{
		registry = new TeamRegistry(gson);
		String stored = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_TEAM_DATA);
		registry.loadFromJson(stored);

		overlay = new EventTeamsOverlay(client, config, registry, chatIconManager);
		overlayManager.add(overlay);

		minimapOverlay = new EventTeamsMinimapOverlay(client, config, registry);
		overlayManager.add(minimapOverlay);

		panel = new EventTeamsPanel(this, registry);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Event Teams")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		refreshOnlineStatus();
		log.debug("Event Teams started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(minimapOverlay);
		clientToolbar.removeNavigation(navButton);
		log.debug("Event Teams stopped");
	}

	void saveTeams()
	{
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_TEAM_DATA, registry.serialize());
	}

	/**
	 * ClanChannelChanged fires when the local player connects to or leaves a
	 * clan channel; ClanMemberJoined/Left fire as individual members log in and
	 * out (or hop worlds). Together they keep the panel's online dots current.
	 * The clan channel is the "currently connected" view — distinct from
	 * ClanSettings, which is the full roster including offline members.
	 */
	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		refreshOnlineStatus();
	}

	@Subscribe
	public void onClanMemberJoined(ClanMemberJoined event)
	{
		refreshOnlineStatus();
	}

	@Subscribe
	public void onClanMemberLeft(ClanMemberLeft event)
	{
		refreshOnlineStatus();
	}

	private void refreshOnlineStatus()
	{
		Map<String, Integer> onlineWorlds = new HashMap<>();

		// Guest channel included so events spanning a guest clan still track.
		for (ClanChannel channel : new ClanChannel[]{client.getClanChannel(), client.getGuestClanChannel()})
		{
			if (channel == null)
			{
				continue;
			}
			for (ClanChannelMember member : channel.getMembers())
			{
				onlineWorlds.put(TeamRegistry.normalize(member.getName()), member.getWorld());
			}
		}

		if (panel != null)
		{
			panel.updateOnlineStatus(onlineWorlds);
		}
	}

	/**
	 * Tags public/clan chat messages with a "[Team]" prefix, coloring the team
	 * name using OSRS's inline <col=RRGGBB> chat markup and leaving the player's
	 * own name in its default color. Private messages and messages from players
	 * not on a team roster are left untouched.
	 */
	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (!config.tagChatMessages())
		{
			return;
		}

		if (chatMessage.getType() != ChatMessageType.PUBLICCHAT
			&& chatMessage.getType() != ChatMessageType.CLAN_CHAT
			&& chatMessage.getType() != ChatMessageType.CLAN_MESSAGE)
		{
			return;
		}

		MessageNode messageNode = chatMessage.getMessageNode();
		if (messageNode == null)
		{
			return;
		}

		String senderName = Text.removeTags(chatMessage.getName());
		if (senderName.isEmpty())
		{
			// System broadcasts (loot drops, level-ups, pbs...) have no sender;
			// the player's name is the start of the message body instead.
			if (chatMessage.getType() == ChatMessageType.CLAN_MESSAGE)
			{
				tagBroadcast(messageNode);
			}
			return;
		}

		TeamInfo team = registry.getTeamForPlayer(senderName);
		if (team == null)
		{
			return;
		}

		if (chatMessage.getType() == ChatMessageType.PUBLICCHAT)
		{
			// Color only the team name; brackets and the player's own name keep
			// their default color. Matches the clan-chat path below, where the
			// client draws the (uncolored) brackets around the sender itself.
			String teamTag = "[" + ColorUtil.wrapWithColorTag(team.getName(), team.getColor()) + "]";
			String taggedName = teamTag + " " + messageNode.getName();

			clientThread.invokeLater(() -> {
				messageNode.setName(taggedName);
				client.refreshChat();
			});
			return;
		}

		// Clan chat resolves the sender's rank icon by looking the name up in
		// the channel when the line is (re)built — rewriting the name breaks
		// that lookup and demotes everyone's icon to guest. Instead, swap the
		// sender (the "[Clan Name]" prefix slot) for the team tag, giving
		// "[Team Name] <icon>Player X: message" with the icon intact.
		String teamTag = ColorUtil.wrapWithColorTag(team.getName(), team.getColor());

		clientThread.invokeLater(() -> {
			messageNode.setSender(teamTag);
			client.refreshChat();
		});
	}

	/**
	 * Broadcasts look like "Player X received a drop: Twisted bow." — if the
	 * message starts with a roster player's name, add the colored team tag as the
	 * sender and leave the message body (including the player's name) untouched,
	 * consistent with the public/clan chat tagging. Matching is done on the raw
	 * value (names never contain markup) with in-game non-breaking spaces treated
	 * as regular spaces.
	 */
	private void tagBroadcast(MessageNode messageNode)
	{
		String value = messageNode.getValue();
		if (value == null || value.isEmpty())
		{
			return;
		}

		String haystack = value.replace('\u00A0', ' ');
		for (TeamInfo team : registry.getTeams())
		{
			for (String player : team.getPlayerNames())
			{
				String candidate = player.replace('\u00A0', ' ').trim();
				if (candidate.isEmpty()
					|| !haystack.regionMatches(true, 0, candidate, 0, candidate.length()))
				{
					continue;
				}
				// Require a word boundary so "Bob" doesn't match "Bobby has..."
				if (haystack.length() > candidate.length()
					&& Character.isLetterOrDigit(haystack.charAt(candidate.length())))
				{
					continue;
				}

				String teamTag = ColorUtil.wrapWithColorTag(team.getName(), team.getColor());

				clientThread.invokeLater(() -> {
					messageNode.setSender(teamTag);
					client.refreshChat();
				});
				return;
			}
		}
	}
}
