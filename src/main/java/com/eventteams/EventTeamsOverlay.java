package com.eventteams;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.FriendsChatRank;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.Text;

/**
 * Draws team-colored names above players in the game world, mirroring the
 * technique the core PlayerIndicators plugin uses (draw text at the player's
 * canvas text location) — just keyed off our team registry instead of
 * friend/clan flags. Minimap dots live in {@link EventTeamsMinimapOverlay},
 * which needs a different overlay layer.
 *
 * The name rendering intentionally duplicates PlayerIndicatorsOverlay's exact
 * layout math (zOffset, rank-icon offsets, sanitized name) and draws at a
 * higher priority, so for players core Player Indicators already labels
 * (friends/clan), our team-colored name paints pixel-for-pixel over the top of
 * theirs — same rank icon, different color — instead of overlapping it.
 */
public class EventTeamsOverlay extends Overlay
{
	// Same value as PlayerIndicatorsOverlay.ACTOR_OVERHEAD_TEXT_MARGIN; keep in
	// sync or the two names drift apart vertically and both become visible.
	private static final int ACTOR_OVERHEAD_TEXT_MARGIN = 40;

	private final Client client;
	private final EventTeamsConfig config;
	private final TeamRegistry registry;
	private final ChatIconManager chatIconManager;

	@Inject
	EventTeamsOverlay(Client client, EventTeamsConfig config, TeamRegistry registry,
		ChatIconManager chatIconManager)
	{
		this.client = client;
		this.config = config;
		this.registry = registry;
		this.chatIconManager = chatIconManager;
		setPosition(OverlayPosition.DYNAMIC);
		// Stay on the default UNDER_WIDGETS layer: that's where core Player
		// Indicators draws (it never calls setLayer), and layer ordering trumps
		// priority — an ABOVE_SCENE overlay always paints before UNDER_WIDGETS.
		// Within the shared layer, our higher priority renders after (on top
		// of) Player Indicators' PRIORITY_MED.
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightInWorld() && !config.highlightOnMinimap())
		{
			return null;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}

		for (Player player : worldView.players())
		{
			if (player == null || player.getName() == null)
			{
				continue;
			}

			Color color = registry.getColorForPlayer(player.getName());
			if (color == null)
			{
				continue;
			}

			// Never label the local player; you know which team you're on.
			if (config.highlightInWorld() && player != client.getLocalPlayer())
			{
				renderPlayerName(graphics, player, color);
			}
		}

		return null;
	}

	private void renderPlayerName(Graphics2D graphics, Player player, Color color)
	{
		String name = Text.sanitize(player.getName());
		int zOffset = player.getLogicalHeight() + ACTOR_OVERHEAD_TEXT_MARGIN;
		Point textLocation = player.getCanvasTextLocation(graphics, name, zOffset);
		if (textLocation == null)
		{
			return;
		}

		BufferedImage rankImage = getRankImage(player);
		if (rankImage != null)
		{
			// We deliberately don't draw the rank icon ourselves, but core
			// Player Indicators shifts the name right by half the icon's width
			// when one applies — mirror that shift so our text still lands
			// exactly on top of theirs (their icon shows through unchanged).
			textLocation = new Point(textLocation.getX() + rankImage.getWidth() / 2, textLocation.getY());
		}

		OverlayUtil.renderTextLocation(graphics, textLocation, name, color);
	}

	/**
	 * Same icon and precedence core Player Indicators shows: friends chat rank
	 * first, otherwise clan rank title.
	 */
	private BufferedImage getRankImage(Player player)
	{
		if (player.isFriendsChatMember())
		{
			FriendsChatManager friendsChatManager = client.getFriendsChatManager();
			FriendsChatMember member = friendsChatManager == null
				? null : friendsChatManager.findByName(Text.removeTags(player.getName()));
			if (member != null && member.getRank() != FriendsChatRank.UNRANKED)
			{
				return chatIconManager.getRankImage(member.getRank());
			}
		}

		if (player.isClanMember())
		{
			ClanChannel clanChannel = client.getClanChannel();
			ClanSettings clanSettings = client.getClanSettings();
			if (clanChannel != null && clanSettings != null)
			{
				ClanChannelMember member = clanChannel.findMember(player.getName());
				if (member != null)
				{
					ClanTitle title = clanSettings.titleForRank(member.getRank());
					if (title != null)
					{
						return chatIconManager.getRankImage(title);
					}
				}
			}
		}

		return null;
	}
}
