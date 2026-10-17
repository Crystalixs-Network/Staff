package net.crystalixs.team;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

@Plugin(
        id = "team",
        name = "Team",
        version = "1.1.2",
        description = "Netzwerkweites Team-Plugin mit MiniMessage und LuckPerms API",
        authors = {"thylexx"}
)
public class Team {

    private final ProxyServer server;
    private final Logger logger;
    private final MiniMessage miniMessage;

    private boolean showDetailedRankInformation = false;

    private LuckPerms luckPerms;

    @Inject
    public Team(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.miniMessage = MiniMessage.miniMessage();
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

    }

    private void sendTeamMessage(Player sender, String message) {
        String serverName = sender.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("Unbekannt");
        String rankDisplay = getPlayerRankDisplay(sender);

        String rawMessage = "<dark_purple><b>@Team</b> <gray>(<light_purple>" + serverName + ") " + rankDisplay + " <gray>" + sender.getUsername() + " » " + message;
        Component formatted = miniMessage.deserialize(rawMessage);

        for (Player p : server.getAllPlayers()) {
            if (p.hasPermission("teamplugin.use")) {
                p.sendMessage(formatted);
            }
        }
    }

    private String getPlayerRankDisplay(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return showDetailedRankInformation ? "<gray>Mitglied" : "<gray>[Team]";
        }

        if (showDetailedRankInformation) {
            String primaryGroup = user.getPrimaryGroup();
            String capitalized = primaryGroup.substring(0, 1).toUpperCase() + primaryGroup.substring(1);
            return "<gold>" + capitalized;
        } else {
            String prefix = user.getCachedData().getMetaData().getPrefix();
            if (prefix == null) {
                return "<gray>[Team]";
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

    private class TeamChatCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!(source instanceof Player player)) {
                source.sendMessage(miniMessage.deserialize("<red>Dieser Befehl ist nur für Spieler."));
                return;
            }

            if (!player.hasPermission("teamplugin.use")) {
                player.sendMessage(miniMessage.deserialize("<red>Dazu hast du keine Rechte."));
                return;
            }

            String[] args = invocation.arguments();
            if (args.length == 0) {
                player.sendMessage(miniMessage.deserialize("<red>Verwendung: /teamchat <Nachricht>"));
                return;
            }

            sendTeamMessage(player, String.join(" ", args));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("teamplugin.use");
        }
    }

    private class TeamListCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!source.hasPermission("teamplugin.use")) {
                source.sendMessage(miniMessage.deserialize("<red>Dazu hast du keine Rechte."));
                return;
            }

            String[] args = invocation.arguments();

            if (args.length >= 2 && args[0].equalsIgnoreCase("attribute") && args[1].equalsIgnoreCase("showDetailedRankInformation")) {
                showDetailedRankInformation = !showDetailedRankInformation;
                source.sendMessage(miniMessage.deserialize("<green>Attribut 'showDetailedRankInformation' ist jetzt: <light_purple>" + showDetailedRankInformation));
                return;
            }

            source.sendMessage(miniMessage.deserialize("<dark_gray>------ <gray>Online Teammitglieder <dark_gray>------" + "<br>"));

            Map<String, List<String>> serverMap = new TreeMap<>();
            int totalCount = 0;

            for (Player player : server.getAllPlayers()) {
                if (player.hasPermission("teamplugin.use")) {
                    String serverName = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("Unbekannt");
                    String rankDisplay = getPlayerRankDisplay(player);

                    String displayName = rankDisplay + player.getUsername();

                    serverMap.computeIfAbsent(serverName, _ -> new ArrayList<>()).add(displayName);
                    totalCount++;
                }
            }

            if (serverMap.isEmpty()) {
                source.sendMessage(miniMessage.deserialize("<gray>Keine Teammitglieder online."));
                return;
            }

            for (Map.Entry<String, List<String>> entry : serverMap.entrySet()) {
                String serverName = entry.getKey();
                List<String> players = entry.getValue();

                String playerListString = String.join(", ", players);

                String lineRaw = "<light_purple>" + serverName + " <dark_gray>(" + players.size() + "): <white>" + playerListString + "<br>";
                source.sendMessage(miniMessage.deserialize(lineRaw));
            }

            source.sendMessage(miniMessage.deserialize("<gray>Insgesamt online: <light_purple>" + totalCount));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("teamplugin.use");
        }
    }
}