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
import net.runelite.api.events.ScriptCallbackEvent;
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
	 * Colors a rostered player's name in their team color in public and clan
	 * chat, using OSRS's inline <col=RRGGBB> chat markup, leaving the rest of
	 * the line (including the clan channel tag) untouched. Private messages and
	 * messages from players not on a team roster are left untouched.
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

		// Color the sender's name in their team color, for both public and clan
		// chat, and leave every other part of the line alone — including the
		// clan channel tag (the "[Clan Name]" sender slot). Wrapping the stored
		// name in only a <col> tag is safe for rank icons: they're resolved by
		// matching Text.removeTags(name), which strips the color tag, so the icon
		// still resolves. The optional team-name prefix is deliberately NOT baked
		// into the stored name here — a literal "[Team]" would survive removeTags
		// and break that lookup (demoting clan icons to guest). It's added to the
		// transient build stack instead, in onScriptCallbackEvent below.
		String coloredName = ColorUtil.wrapWithColorTag(messageNode.getName(), team.getColor());

		clientThread.invokeLater(() -> {
			messageNode.setName(coloredName);
			client.refreshChat();
		});
	}

	/**
	 * Adds the optional team tag to public/clan chat lines as they are built, by
	 * editing the object stack rather than the stored MessageNode name. The chat
	 * build stack holds [channel/clan-tag = size-4, username = size-3, message =
	 * size-2]. The clan rank icon is resolved from the username (size-3) *after*
	 * this callback, so that slot must stay clean — a literal "[Team]" there
	 * would fail the lookup and demote everyone's icon to guest.
	 *
	 * So in clan chat we append the team tag to the clan-tag slot (size-4),
	 * rendering "[Clan] [Team] &lt;icon&gt;Player" with the icon intact. Public
	 * chat has no clan tag, so there we prefix the username slot directly.
	 */
	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!"chatMessageBuilding".equals(event.getEventName())
			|| !config.tagChatMessages() || !config.showTeamNameInChat())
		{
			return;
		}

		int uid = client.getIntStack()[client.getIntStackSize() - 1];
		MessageNode messageNode = client.getMessages().get(uid);
		if (messageNode == null)
		{
			return;
		}

		ChatMessageType type = messageNode.getType();
		Object[] objectStack = client.getObjectStack();
		int size = client.getObjectStackSize();

		// Username slot is read (tags stripped) to identify the team either way.
		String name = (String) objectStack[size - 3];
		TeamInfo team = registry.getTeamForPlayer(Text.removeTags(name));
		if (team == null)
		{
			return;
		}

		String coloredTeam = ColorUtil.wrapWithColorTag(team.getName(), team.getColor());

		if (type == ChatMessageType.CLAN_CHAT)
		{
			// Append to the clan-tag slot so it renders as a separate bracketed
			// tag right after the clan tag; the username slot is left untouched
			// so the clan rank icon still resolves. ChatMessageManager wraps this
			// whole slot in the clan color, so close that color (</col>) before
			// the "] [" separator — otherwise the brackets between the two tags
			// inherit the clan color instead of the default text color.
			String channel = (String) objectStack[size - 4];
			objectStack[size - 4] = channel + "</col>] [" + coloredTeam;
		}
		else if (type == ChatMessageType.PUBLICCHAT)
		{
			objectStack[size - 3] = "[" + coloredTeam + "] " + name;
		}
	}

	/**
	 * The optional "[Team Name] " prefix (only the name within the brackets is
	 * team-colored; a trailing space follows) shown before a player's name when
	 * the config toggle is on, or "" when off.
	 */
	private String teamPrefix(TeamInfo team)
	{
		if (!config.showTeamNameInChat())
		{
			return "";
		}
		return "[" + ColorUtil.wrapWithColorTag(team.getName(), team.getColor()) + "] ";
	}

	/**
	 * Broadcasts look like "Player X received a drop: Twisted bow." — if the
	 * message starts with a roster player's name, color just that name in the
	 * team color and leave the rest of the line untouched, consistent with the
	 * public/clan chat coloring. Matching is done on the raw value (names never
	 * contain markup) with in-game non-breaking spaces treated as regular spaces.
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

				String namePart = value.substring(0, candidate.length());
				String rest = value.substring(candidate.length());
				String colored = teamPrefix(team)
					+ ColorUtil.wrapWithColorTag(namePart, team.getColor()) + rest;

				clientThread.invokeLater(() -> {
					messageNode.setValue(colored);
					client.refreshChat();
				});
				return;
			}
		}
	}
}
