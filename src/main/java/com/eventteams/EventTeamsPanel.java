package com.eventteams;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar UI. One card per team: editable name, color, the team's players each
 * with a live online dot (from the clan channel), a one-at-a-time add field,
 * and a clipboard paste for a JSON array of names. Top-level buttons copy or
 * replace the entire team setup as shareable JSON.
 *
 * Uses PluginPanel's default wrapping (single fixed-width column, vertical
 * scrollbar only) so nothing can overflow horizontally.
 */
public class EventTeamsPanel extends PluginPanel
{
	private static final Color ONLINE_COLOR = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color OFFLINE_COLOR = ColorScheme.MEDIUM_GRAY_COLOR;

	private final EventTeamsPlugin plugin;
	private final TeamRegistry registry;

	private final JPanel teamsContainer = new JPanel(new DynamicGridLayout(0, 1, 0, 8));

	// Re-run on every status update so player rows recolor in place without a
	// full rebuild (a rebuild would steal focus from the add-player fields).
	private final List<Runnable> statusUpdaters = new ArrayList<>();
	private Map<String, Integer> onlineWorlds = Collections.emptyMap();

	// Offline sections are collapsed unless toggled open; keyed by team so the
	// choice survives the rebuilds that status changes trigger.
	private final Map<TeamInfo, Boolean> offlineExpanded = new HashMap<>();

	public EventTeamsPanel(EventTeamsPlugin plugin, TeamRegistry registry)
	{
		this.plugin = plugin;
		this.registry = registry;

		JPanel actions = new JPanel(new DynamicGridLayout(0, 1, 0, 4));

		JButton addTeamButton = new JButton("+ Add Team");
		addTeamButton.addActionListener(e -> {
			registry.addTeam(new TeamInfo("Team " + (registry.getTeams().size() + 1), randomColor()));
			plugin.saveTeams();
			rebuildTeamsUi();
		});

		JButton exportButton = new JButton("Export teams JSON");
		exportButton.setToolTipText("Copy all teams (names, colors, players) to the clipboard as JSON");
		exportButton.addActionListener(e ->
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(registry.toShareJson()), null));

		JButton importButton = new JButton("Import teams JSON");
		importButton.setToolTipText("Replace all teams with JSON from the clipboard (the format Export produces)");
		importButton.addActionListener(e -> importTeamsFromClipboard());

		actions.add(addTeamButton);
		actions.add(exportButton);
		actions.add(importButton);

		add(actions);
		add(teamsContainer);

		rebuildTeamsUi();
	}

	private void rebuildTeamsUi()
	{
		statusUpdaters.clear();
		teamsContainer.removeAll();
		for (TeamInfo team : registry.getTeams())
		{
			teamsContainer.add(buildTeamCard(team));
		}
		teamsContainer.revalidate();
		teamsContainer.repaint();
	}

	private JPanel buildTeamCard(TeamInfo team)
	{
		JPanel card = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)));

		JTextField nameField = new JTextField(team.getName());
		nameField.addActionListener(e -> saveTeamName(team, nameField));
		nameField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				saveTeamName(team, nameField);
			}
		});

		JButton colorButton = new JButton("Color");
		colorButton.setBackground(team.getColor());
		colorButton.addActionListener(e -> {
			Color chosen = JColorChooser.showDialog(card, "Team color", team.getColor());
			if (chosen != null)
			{
				team.setColor(chosen);
				colorButton.setBackground(chosen);
				plugin.saveTeams();
			}
		});

		JButton removeButton = new JButton("✕");
		removeButton.setMargin(new Insets(0, 6, 0, 6));
		removeButton.setToolTipText("Remove team");
		removeButton.addActionListener(e -> {
			registry.removeTeam(team);
			plugin.saveTeams();
			rebuildTeamsUi();
		});

		JPanel nameRow = new JPanel(new BorderLayout(4, 0));
		nameRow.add(nameField, BorderLayout.CENTER);
		nameRow.add(removeButton, BorderLayout.EAST);

		card.add(nameRow);
		card.add(colorButton);

		// Online players first, offline below, each alphabetical.
		List<String> online = new ArrayList<>();
		List<String> offline = new ArrayList<>();
		for (String playerName : team.getPlayerNames())
		{
			(isOnline(playerName) ? online : offline).add(playerName);
		}
		online.sort(String.CASE_INSENSITIVE_ORDER);
		offline.sort(String.CASE_INSENSITIVE_ORDER);

		if (!online.isEmpty())
		{
			card.add(sectionHeader("Online"));
			for (String playerName : online)
			{
				card.add(buildPlayerRow(team, playerName));
			}
		}
		if (!offline.isEmpty())
		{
			boolean expanded = offlineExpanded.getOrDefault(team, false);
			card.add(offlineToggle(team, offline.size(), expanded));
			if (expanded)
			{
				for (String playerName : offline)
				{
					card.add(buildPlayerRow(team, playerName));
				}
			}
		}

		JTextField addField = new JTextField();
		JButton addButton = new JButton("+");
		addButton.setMargin(new Insets(0, 6, 0, 6));
		Runnable submitPlayer = () -> {
			String name = addField.getText().trim();
			if (name.isEmpty())
			{
				return;
			}
			if (!containsName(team, name))
			{
				team.getPlayerNames().add(name);
				registry.reindex();
				plugin.saveTeams();
			}
			rebuildTeamsUi();
		};
		addField.addActionListener(e -> submitPlayer.run());
		addButton.addActionListener(e -> submitPlayer.run());

		JPanel addRow = new JPanel(new BorderLayout(4, 0));
		addRow.add(addField, BorderLayout.CENTER);
		addRow.add(addButton, BorderLayout.EAST);
		card.add(addRow);

		return card;
	}

	private static JLabel sectionHeader(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
		return label;
	}

	/** Muted, clickable "Offline (n)" header that expands/collapses the section. */
	private JLabel offlineToggle(TeamInfo team, int count, boolean expanded)
	{
		JLabel label = sectionHeader((expanded ? "▾ " : "▸ ") + "Offline (" + count + ")");
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.setToolTipText(expanded ? "Hide offline players" : "Show offline players");
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				offlineExpanded.put(team, !expanded);
				rebuildTeamsUi();
			}
		});
		return label;
	}

	private JPanel buildPlayerRow(TeamInfo team, String playerName)
	{
		JLabel dot = new JLabel("●");
		JLabel nameLabel = new JLabel(playerName);
		JLabel worldLabel = new JLabel();
		worldLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JButton removeButton = new JButton("✕");
		removeButton.setMargin(new Insets(0, 4, 0, 4));
		removeButton.setToolTipText("Remove " + playerName);
		removeButton.addActionListener(e -> {
			team.getPlayerNames().remove(playerName);
			registry.reindex();
			plugin.saveTeams();
			rebuildTeamsUi();
		});

		JPanel east = new JPanel(new BorderLayout(4, 0));
		east.add(worldLabel, BorderLayout.CENTER);
		east.add(removeButton, BorderLayout.EAST);

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.add(dot, BorderLayout.WEST);
		row.add(nameLabel, BorderLayout.CENTER);
		row.add(east, BorderLayout.EAST);

		Runnable updater = () -> {
			Integer world = onlineWorlds.get(TeamRegistry.normalize(playerName));
			boolean online = world != null;
			dot.setForeground(online ? ONLINE_COLOR : OFFLINE_COLOR);
			worldLabel.setText(online ? "W" + world : "");
			nameLabel.setToolTipText(online
				? playerName + " is online (world " + world + ")"
				: playerName + " is offline (or not in the clan channel)");
		};
		updater.run();
		statusUpdaters.add(updater);

		return row;
	}

	private void importTeamsFromClipboard()
	{
		String clip = readClipboard();
		List<TeamInfo> imported = clip == null ? null : registry.fromShareJson(clip);
		if (imported == null)
		{
			JOptionPane.showMessageDialog(this,
				"Clipboard doesn't contain valid teams JSON.\n"
					+ "Expected: [{\"name\": ..., \"color\": \"#RRGGBB\", \"players\": [...]}]",
				"Paste teams", JOptionPane.WARNING_MESSAGE);
			return;
		}

		if (!registry.getTeams().isEmpty()
			&& JOptionPane.showConfirmDialog(this,
				"Replace all " + registry.getTeams().size() + " existing team(s)?",
				"Paste teams", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
		{
			return;
		}

		registry.setTeams(imported);
		plugin.saveTeams();
		rebuildTeamsUi();
	}

	private static boolean containsName(TeamInfo team, String name)
	{
		String normalized = TeamRegistry.normalize(name);
		for (String existing : team.getPlayerNames())
		{
			if (TeamRegistry.normalize(existing).equals(normalized))
			{
				return true;
			}
		}
		return false;
	}

	private void saveTeamName(TeamInfo team, JTextField field)
	{
		String name = field.getText().trim();
		if (!name.isEmpty() && !name.equals(team.getName()))
		{
			team.setName(name);
			plugin.saveTeams();
		}
	}

	private static String readClipboard()
	{
		try
		{
			return (String) Toolkit.getDefaultToolkit().getSystemClipboard()
				.getData(DataFlavor.stringFlavor);
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	/** Called by the plugin whenever the clan channel updates; safe off the EDT. */
	public void updateOnlineStatus(Map<String, Integer> onlineWorlds)
	{
		SwingUtilities.invokeLater(() -> {
			// Row order depends on who's online, so a membership change needs a
			// rebuild; a mere world hop only needs the in-place label updates
			// (which don't steal focus from the add-player fields).
			boolean orderChanged = !onlineRosterNames(onlineWorlds).equals(onlineRosterNames(this.onlineWorlds));
			this.onlineWorlds = onlineWorlds;
			if (orderChanged)
			{
				rebuildTeamsUi();
			}
			else
			{
				for (Runnable updater : statusUpdaters)
				{
					updater.run();
				}
			}
		});
	}

	private boolean isOnline(String playerName)
	{
		return onlineWorlds.containsKey(TeamRegistry.normalize(playerName));
	}

	/** Normalized roster names currently online per the given channel snapshot. */
	private Set<String> onlineRosterNames(Map<String, Integer> worlds)
	{
		Set<String> out = new HashSet<>();
		for (TeamInfo team : registry.getTeams())
		{
			for (String playerName : team.getPlayerNames())
			{
				String normalized = TeamRegistry.normalize(playerName);
				if (worlds.containsKey(normalized))
				{
					out.add(normalized);
				}
			}
		}
		return out;
	}

	private static Color randomColor()
	{
		java.util.Random r = new java.util.Random();
		return Color.getHSBColor(r.nextFloat(), 0.65f, 0.95f);
	}
}
