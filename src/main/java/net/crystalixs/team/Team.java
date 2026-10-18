package net.crystalixs.team;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

@Plugin(
        id = "team",
        name = "Team",
        version = "v1.1.3",
        description = "TeamPlugin von Crystalixs.net",
        authors = {"thylexx"}
)
public class Team {

    private final ProxyServer server;
    private final Logger logger;
    private final MiniMessage miniMessage;
    private final Path dataDirectory;
    private final File configFile;

    private final Map<UUID, Boolean> detailedRankMap = new HashMap<>();

    private LuckPerms luckPerms;

    @Inject
    public Team(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.miniMessage = MiniMessage.miniMessage();
        this.dataDirectory = dataDirectory;
        this.configFile = new File(dataDirectory.toFile(), "player_attributes.properties");
        loadConfig();
    }

    private void loadConfig() {
        Properties properties = new Properties();
        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                properties.load(in);
                for (String key : properties.stringPropertyNames()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        boolean value = Boolean.parseBoolean(properties.getProperty(key));
                        detailedRankMap.put(uuid, value);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            } catch (IOException e) {
                logger.severe("Konnte die Spieler-Attribute nicht laden: " + e.getMessage());
            }
        }
    }

    private void saveConfig() {
        File parentDir = dataDirectory.toFile();
        if (!parentDir.exists()) {
            boolean mkdirs;
            if (parentDir.mkdirs()) mkdirs = true;
            else mkdirs = false;
        }
        Properties properties = new Properties();
        for (Map.Entry<UUID, Boolean> entry : detailedRankMap.entrySet()) {
            properties.setProperty(entry.getKey().toString(), String.valueOf(entry.getValue()));
        }
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            properties.store(out, "TeamPlugin Player Attributes");
        } catch (IOException e) {
            logger.severe("Konnte die Spieler-Attribute nicht speichern: " + e.getMessage());
        }
    }

    private boolean isDetailedRankEnabled(Player viewer) {
        return detailedRankMap.getOrDefault(viewer.getUniqueId(), false);
    }

    private void toggleDetailedRank(Player viewer) {
        boolean current = isDetailedRankEnabled(viewer);
        detailedRankMap.put(viewer.getUniqueId(), !current);
        saveConfig();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            this.luckPerms = LuckPermsProvider.get();
            logger.info("LuckPerms API erfolgreich angebunden!");
        } catch (IllegalStateException e) {
            logger.severe("LuckPerms wurde nicht gefunden! Das Plugin benötigt LuckPerms.");
            return;
        }

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("teamchat").aliases("tc").build(),
                new TeamChatCommand()
        );

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("teamlist").aliases("tl").build(),
                new TeamListCommand()
        );

        logger.info("TeamPlugin wurde erfolgreich gestartet!");
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        if (message.startsWith("@") && player.hasPermission("crystalixs.team.member")) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            String teamMessage = message.substring(1).trim();
            if (!teamMessage.isEmpty()) {
                sendTeamMessage(player, teamMessage);
            }
        }
    }

    private void sendTeamMessage(Player sender, String message) {
        String serverName = getGroupedServerName(sender.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("Unbekannt"));

        String rankDisplay = getPlayerRankDisplay(sender, false);

        String rawMessage = "<dark_purple><b>@Team</b> <gray>(<light_purple>" + serverName + "<gray>) " + rankDisplay + " <gray>" + sender.getUsername() + " » " + message;
        Component formatted = miniMessage.deserialize(rawMessage);

        for (Player p : server.getAllPlayers()) {
            if (p.hasPermission("crystalixs.team.member")) {
                p.sendMessage(formatted);
            }
        }
    }

    private String getPlayerRankDisplay(Player targetPlayer, boolean useDetailed) {
        User user = luckPerms.getUserManager().getUser(targetPlayer.getUniqueId());
        if (user == null) {
            return "<red>unbekannt";
        }

        if (useDetailed) {
            String primaryGroupName = user.getPrimaryGroup();
            Group group = luckPerms.getGroupManager().getGroup(primaryGroupName);

            if (group != null) {
                String displayName = group.getDisplayName();
                if (displayName != null && !displayName.equalsIgnoreCase(primaryGroupName)) {
                    return convertLegacyToMiniMessage(displayName);
                }
            }

            String capitalized = primaryGroupName.substring(0, 1).toUpperCase() + primaryGroupName.substring(1);
            return "<gray>" + capitalized;
        } else {
            String prefix = user.getCachedData().getMetaData().getPrefix();
            if (prefix == null) {
                return "<red>unbekannt";
            }
            return convertLegacyToMiniMessage(prefix);
        }
    }

    private String convertLegacyToMiniMessage(String legacyText) {
        return legacyText
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&l", "<bold>")
                .replace("&o", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&r", "<reset>");
    }

    private String getGroupedServerName(String rawServerName) {
        String lower = rawServerName.toLowerCase();

        if (lower.startsWith("citybuild")) {
            return "citybuild";
        }
        if (lower.startsWith("islands")) {
            return "islands";
        }

        return rawServerName;
    }

    private class TeamChatCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!(source instanceof Player player)) {
                source.sendMessage(miniMessage.deserialize("<dark_purple><b>Crystalixs</b> <gray>» <red>Dieser Befehl ist nur für Spieler."));
                return;
            }

            if (!player.hasPermission("crystalixs.team.member")) {
                player.sendMessage(miniMessage.deserialize("<dark_purple><b>Crystalixs</b> <gray>» <red>Dazu hast du keine Rechte."));
                return;
            }

            String[] args = invocation.arguments();
            if (args.length == 0) {
                player.sendMessage(miniMessage.deserialize("<dark_purple><b>Crystalixs</b> <gray>» <red>Verwendung: /teamchat <Nachricht>"));
                return;
            }

            sendTeamMessage(player, String.join(" ", args));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("crystalixs.team.member");
        }
    }

    private class TeamListCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!(source instanceof Player viewer)) {
                source.sendMessage(miniMessage.deserialize("<dark_purple><b>Crystalixs</b> <gray>» <red>Dieser Befehl ist nur für Spieler."));
                return;
            }

            if (!viewer.hasPermission("crystalixs.team.member")) {
                viewer.sendMessage(miniMessage.deserialize("<dark_purple><b>Crystalixs</b> <gray>» <red>Dazu hast du keine Rechte."));
                return;
            }

            String[] args = invocation.arguments();

            if (args.length >= 2 && args[0].equalsIgnoreCase("attribute") && args[1].equalsIgnoreCase("showDetailedRankInformation")) {
                toggleDetailedRank(viewer);
                boolean newState = isDetailedRankEnabled(viewer);
                viewer.sendMessage(miniMessage.deserialize("<dark_purple><b>Crystalixs</b> <gray>» Dein Attribut <light_purple>'showDetailedRankInformation'<gray> ist jetzt: <light_purple>" + newState));
                return;
            }

            viewer.sendMessage(miniMessage.deserialize("<dark_gray>------ <gray>Online Teammitglieder ------"));

            Map<String, List<String>> serverMap = new TreeMap<>();

            for (RegisteredServer registeredServer : server.getAllServers()) {
                String groupedName = getGroupedServerName(registeredServer.getServerInfo().getName());
                serverMap.putIfAbsent(groupedName, new ArrayList<>());
            }

            boolean showDetailedForViewer = isDetailedRankEnabled(viewer);

            int totalCount = 0;
            for (Player targetPlayer : server.getAllPlayers()) {
                if (targetPlayer.hasPermission("crystalixs.team.member")) {
                    String rawServerName = targetPlayer.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("Unbekannt");
                    String groupedServerName = getGroupedServerName(rawServerName);

                    String rankDisplay = getPlayerRankDisplay(targetPlayer, showDetailedForViewer);
                    String formattedPlayerName = rankDisplay + " " + targetPlayer.getUsername();

                    serverMap.computeIfAbsent(groupedServerName, _ -> new ArrayList<>()).add(formattedPlayerName);
                    totalCount++;
                }
            }

            for (Map.Entry<String, List<String>> entry : serverMap.entrySet()) {
                String serverName = entry.getKey();
                List<String> players = entry.getValue();

                String playerListString = players.isEmpty() ? "<dark_gray>keine" : String.join("<gray>, ", players);

                String lineRaw = "<light_purple>" + serverName + " <gray>(" + players.size() + "): " + playerListString;
                viewer.sendMessage(miniMessage.deserialize(lineRaw));
            }

            viewer.sendMessage(miniMessage.deserialize("<gray>Insgesamt online: <light_purple>" + totalCount));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("crystalixs.team.member");
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            if (!invocation.source().hasPermission("crystalixs.team.member")) {
                return Collections.emptyList();
            }

            String[] args = invocation.arguments();
            if (args.length == 1) {
                return filterCompletions(args[0], Collections.singletonList("attribute"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("attribute")) {
                return filterCompletions(args[1], Collections.singletonList("showDetailedRankInformation"));
            }

            return Collections.emptyList();
        }

        private List<String> filterCompletions(String currentInput, List<String> options) {
            List<String> result = new ArrayList<>();
            String lowerInput = currentInput.toLowerCase();
            for (String option : options) {
                if (option.toLowerCase().startsWith(lowerInput)) {
                    result.add(option);
                }
            }
            return result;
        }
    }
}