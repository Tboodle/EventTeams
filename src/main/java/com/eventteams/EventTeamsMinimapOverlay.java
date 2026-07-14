package com.eventteams;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.Text;

/**
 * Draws team-colored minimap dots. Kept separate from {@link EventTeamsOverlay}
 * because the minimap is part of the widget/interface layer: to paint a dot on
 * top of the game's own minimap dots (e.g. the native green friend dot) the
 * overlay must sit on ABOVE_WIDGETS, whereas the in-world name text belongs on
 * the default UNDER_WIDGETS layer so it doesn't paint over the interface. This
 * mirrors core Player Indicators, which likewise uses a dedicated
 * ABOVE_WIDGETS minimap overlay.
 */
public class EventTeamsMinimapOverlay extends Overlay
{
	private final Client client;
	private final EventTeamsConfig config;
	private final TeamRegistry registry;

	@Inject
	EventTeamsMinimapOverlay(Client client, EventTeamsConfig config, TeamRegistry registry)
	{
		this.client = client;
		this.config = config;
		this.registry = registry;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightOnMinimap())
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

			Point minimapLocation = player.getMinimapLocation();
			if (minimapLocation != null)
			{
				OverlayUtil.renderMinimapLocation(graphics, minimapLocation, color);

				if (config.showNamesOnMinimap())
				{
					String name = Text.sanitize(player.getName());
					// Center the name above the dot, matching core Player Indicators.
					Point textLocation = new Point(
						minimapLocation.getX() - graphics.getFontMetrics().stringWidth(name) / 2,
						minimapLocation.getY() - 3);
					OverlayUtil.renderTextLocation(graphics, textLocation, name, color);
				}
			}
		}

		return null;
	}
}
