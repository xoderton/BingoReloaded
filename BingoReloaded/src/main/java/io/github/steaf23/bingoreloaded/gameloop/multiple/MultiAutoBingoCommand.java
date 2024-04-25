package io.github.steaf23.bingoreloaded.gameloop.multiple;

import io.github.steaf23.bingoreloaded.cards.CardSize;
import io.github.steaf23.bingoreloaded.command.BingoCommand;
import io.github.steaf23.bingoreloaded.data.BingoCardData;
import io.github.steaf23.bingoreloaded.data.BingoSettingsData;
import io.github.steaf23.bingoreloaded.gameloop.BingoSession;
import io.github.steaf23.bingoreloaded.gameloop.GameManager;
import io.github.steaf23.bingoreloaded.gui.EffectOptionFlags;
import io.github.steaf23.bingoreloaded.player.BingoParticipant;
import io.github.steaf23.bingoreloaded.player.BingoPlayer;
import io.github.steaf23.bingoreloaded.settings.BingoGamemode;
import io.github.steaf23.bingoreloaded.settings.BingoSettings;
import io.github.steaf23.bingoreloaded.settings.BingoSettingsBuilder;
import io.github.steaf23.bingoreloaded.settings.PlayerKit;
import io.github.steaf23.bingoreloaded.util.Message;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MultiAutoBingoCommand implements TabExecutor
{
    private final GameManager manager;

    public MultiAutoBingoCommand(GameManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender commandSender, @NonNull Command autobingoCommand, @NonNull String alias, @NonNull String[] args) {
        // AutoBingo should only work for admins or console.
        if (commandSender instanceof Player p && !p.hasPermission("bingo.admin")) {
            return false;
        }

        if (args.length < 2) {
            return false;
        }
        String sessionName = args[0];
        String command = args[1];
        String[] extraArguments = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length - 1) : new String[]{};
        BingoSettingsBuilder settings = null;
        BingoSession session = manager.getSession(sessionName);
        if (session != null)
            settings = manager.getSession(sessionName).settingsBuilder;

        // All commands besides "create" can only be executed if a game session exists in the given world
        if (!command.equals("create") && manager.getSession(sessionName) == null) {
            sendFailed("Cannot perform command on a session world that has no valid session!", sessionName);
            return false;
        }

        boolean success = switch (command) {
            case "create" -> create(sessionName);
            case "destroy" -> destroy(sessionName);
            case "start" -> start(sessionName);
            case "kit" -> setKit(session, settings, sessionName, extraArguments);
            case "effects" -> setEffect(session, settings, sessionName, extraArguments);
            case "card" -> setCard(settings, sessionName, extraArguments);
            case "countdown" -> setCountdown(settings, sessionName, extraArguments);
            case "duration" -> setDuration(settings, sessionName, extraArguments);
            case "team" -> setPlayerTeam(sessionName, extraArguments);
            case "teamsize" -> setTeamSize(settings, sessionName, extraArguments);
            case "gamemode" -> setGamemode(settings, sessionName, extraArguments);
            case "end" -> end(sessionName);
            case "preset" -> preset(settings, sessionName, extraArguments);
            default -> {
                Message.log(ChatColor.RED + "Invalid command: '" + command + "' not recognized");
                yield false;
            }
        };

        return success;
    }

    public boolean create(String worldName) {
        manager.createSession(worldName);
        sendSuccess("Connected Bingo Reloaded to this world!", worldName);
        return true;
    }

    public boolean destroy(String worldName) {
        manager.destroySession(worldName);
        sendSuccess("Disconnected Bingo Reloaded from this world!", worldName);
        return true;
    }

    public boolean start(String worldName) {
        if (manager.startGame(worldName)) {
            sendFailed("Could not start game", worldName);
            return false;
        } else {
            sendSuccess("The game has started!", worldName);
            return true;
        }
    }

    public boolean setKit(BingoSession session, BingoSettingsBuilder settings, String worldName, String[] extraArguments) {
        if (extraArguments.length != 1) {
            sendFailed("Expected 3 arguments!", worldName);
            return false;
        }

        PlayerKit kit = PlayerKit.fromConfig(extraArguments[0]);
        settings.kit(kit, session);
        sendSuccess("Kit set to " + kit.getDisplayName(), worldName);
        return true;
    }

    public boolean setEffect(BingoSession session, BingoSettingsBuilder settings, String worldName, String[] extraArguments) {
        // autobingo world effect <effect_name> [true | false]
        // If argument count is only 1, enable all, none or just the single effect typed.
        //     Else default enable effect unless the second argument is "false".

        if (extraArguments.length == 0) {
            sendFailed("Expected at least 3 arguments!", worldName);
            return false;
        }
        String effect = extraArguments[0];
        boolean enable = extraArguments.length > 1 && extraArguments[1].equals("false") ? false : true;

        if (effect.equals("all")) {
            settings.effects(EffectOptionFlags.ALL_ON, session);
            sendSuccess("Updated active effects to " + EffectOptionFlags.ALL_ON, worldName);
            return true;
        } else if (effect.equals("none")) {
            settings.effects(EffectOptionFlags.ALL_OFF, session);
            sendSuccess("Updated active effects to " + EffectOptionFlags.ALL_OFF, worldName);
            return true;
        }

        try {
            settings.toggleEffect(EffectOptionFlags.valueOf(effect.toUpperCase()), enable);
            sendSuccess("Updated active effects to " + settings.view().effects(), worldName);
            return true;
        } catch (IllegalArgumentException e) {
            sendFailed("Invalid effect: " + effect, worldName);
            return false;
        }
    }

    public boolean setCard(BingoSettingsBuilder settings, String worldName, String[] extraArguments) {
        if (extraArguments.length == 0) {
            sendFailed("Expected at least 3 arguments!", worldName);
            return false;
        }

        String cardName = extraArguments[0];
        int seed = extraArguments.length > 1 ? BingoCommand.toInt(extraArguments[1], 0) : 0;

        BingoCardData cardsData = new BingoCardData();
        if (cardsData.getCardNames().contains(cardName)) {
            settings.card(cardName).cardSeed(seed);
            sendSuccess("Playing card set to " + cardName + " with" +
                    (seed == 0 ? " no seed" : " seed " + seed), worldName);
            return true;
        }
        sendFailed("No card named '" + cardName + "' was found!", worldName);
        return false;
    }

    public boolean setCountdown(BingoSettingsBuilder settings, String worldName, String[] extraArguments) {
        if (extraArguments.length != 1) {
            sendFailed("Expected 3 arguments!", worldName);
            return false;
        }

        boolean enableCountdown = extraArguments[0].equals("true");
        settings.enableCountdown(enableCountdown);
        sendSuccess((enableCountdown ? "Enabled" : "Disabled") + " countdown mode", worldName);
        return true;
    }

    public boolean setDuration(BingoSettingsBuilder settings, String worldName, String[] extraArguments) {
        if (extraArguments.length != 1) {
            sendFailed("Expected 3 arguments!", worldName);
            return false;
        }

        int gameDuration = BingoCommand.toInt(extraArguments[0], 0);
        if (gameDuration > 0) {
            settings.countdownGameDuration(gameDuration);
            sendSuccess("Set game duration for countdown mode to " + gameDuration, worldName);
            return true;
        }
        sendFailed("Cannot set duration to " + gameDuration, worldName);
        return true;
    }

    public boolean setPlayerTeam(String sessionName, String[] extraArguments) {
        if (extraArguments.length != 2) {
            sendFailed("Expected 4 arguments!", sessionName);
            return false;
        }

        if (manager.getSession(sessionName) == null) {
            sendFailed("Cannot add player to team, world '" + sessionName + "' is not a bingo world!", sessionName);
            return false;
        }

        BingoSession session = manager.getSession(sessionName);
        String playerName = extraArguments[0];
        String teamName = extraArguments[1];

        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            sendFailed("Cannot add " + playerName + " to team, player does not exist/ is not online!", sessionName);
            return false;
        }

        if (teamName.equalsIgnoreCase("none")) {
            BingoParticipant participant = session.teamManager.getPlayerAsParticipant(player);
            if (participant == null) {
                sendFailed(playerName + " did not join any teams!", sessionName);
                return false;
            }

            session.teamManager.removeMemberFromTeam(participant);
            sendSuccess("Player " + playerName + " removed from all teams", sessionName);
            return true;
        }

        BingoPlayer bingoPlayer = new BingoPlayer(player, session);
        if (!session.teamManager.addMemberToTeam(bingoPlayer, teamName)) {
            sendFailed("Player " + player + " could not be added to team " + teamName, sessionName);
            return false;
        }
        sendSuccess("Player " + playerName + " added to team " + teamName + "", sessionName);
        return true;
    }

    public boolean setTeamSize(BingoSettingsBuilder settings, String worldName, String[] extraArguments) {
        if (extraArguments.length != 1) {
            sendFailed("Expected 3 arguments!", worldName);
            return false;
        }

        int teamSize = Math.min(64, Math.max(1, BingoCommand.toInt(extraArguments[0], 1)));

        settings.maxTeamSize(teamSize);
        sendSuccess("Set maximum team size to " + teamSize + " players", worldName);
        return true;
    }

    public boolean setGamemode(BingoSettingsBuilder settings, String worldName, String[] extraArguments) {
        if (extraArguments.length == 0) {
            sendFailed("Expected at least 3 arguments!", worldName);
            return false;
        }

        try {
            settings.mode(BingoGamemode.fromDataString(extraArguments[0]));
            if (extraArguments[1].equals("3")) {
                settings.cardSize(CardSize.X3);
            } else {
                settings.cardSize(CardSize.X5);
            }
        } catch (IllegalArgumentException e) {
            sendFailed("Cannot set gamemode to '" + extraArguments[0] + "', unknown gamemode!", worldName);
            return false;
        }
        BingoSettings view = settings.view();
        sendSuccess("Set gamemode to " + view.mode().displayName + " " + view.size() + "x" + view.size(), worldName);
        return true;
    }

    public boolean end(String worldName) {
        if (manager.endGame(worldName)) {
            sendFailed("Could not end the game", worldName);
            return false;
        } else {
            sendSuccess("Game forcefully ended!", worldName);
            return true;
        }
    }

    public boolean preset(BingoSettingsBuilder settingsBuilder, String sessionName, String[] extraArguments) {
        if (extraArguments.length != 2) {
            sendFailed("Expected 4 arguments!", sessionName);
            return false;
        }

        BingoSettingsData settingsData = new BingoSettingsData();

        String path = extraArguments[0];
        if (path.isBlank()) {
            sendFailed("Please enter a valid preset name", sessionName);
            return false;
        }
        if (extraArguments[0].equals("save")) {
            settingsData.saveSettings(path, settingsBuilder.view());
        } else if (extraArguments[0].equals("load")) {
            Objects.requireNonNull(manager.getSession(sessionName)).settingsBuilder.fromOther(settingsData.getSettings(path));
        }

        return true;
    }

    private void sendFailed(String message, String sessionName) {
        Message.log(ChatColor.RED + message, sessionName);
    }

    private void sendSuccess(String message, String sessionName) {
        Message.log(ChatColor.GREEN + message, sessionName);
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        return null;
    }
}
