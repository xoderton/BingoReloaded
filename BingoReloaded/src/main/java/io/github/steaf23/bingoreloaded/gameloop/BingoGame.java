package io.github.steaf23.bingoreloaded.gameloop;

import io.github.steaf23.bingoreloaded.*;
import io.github.steaf23.bingoreloaded.cards.BingoCard;
import io.github.steaf23.bingoreloaded.cards.CardBuilder;
import io.github.steaf23.bingoreloaded.data.BingoCardsData;
import io.github.steaf23.bingoreloaded.data.BingoStatType;
import io.github.steaf23.bingoreloaded.data.BingoTranslation;
import io.github.steaf23.bingoreloaded.data.ConfigData;
import io.github.steaf23.bingoreloaded.event.*;
import io.github.steaf23.bingoreloaded.gui.EffectOptionFlags;
import io.github.steaf23.bingoreloaded.item.ItemText;
import io.github.steaf23.bingoreloaded.player.*;
import io.github.steaf23.bingoreloaded.tasks.BingoTask;
import io.github.steaf23.bingoreloaded.tasks.statistics.StatisticTracker;
import io.github.steaf23.bingoreloaded.util.Message;
import io.github.steaf23.bingoreloaded.util.PDCHelper;
import io.github.steaf23.bingoreloaded.util.TranslatedMessage;
import io.github.steaf23.bingoreloaded.util.timer.CountdownTimer;
import io.github.steaf23.bingoreloaded.util.timer.CounterTimer;
import io.github.steaf23.bingoreloaded.util.timer.GameTimer;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.*;

public class BingoGame implements GamePhase
{
    private final BingoSession session;
    private final String worldName;
    private final BingoSettings settings;
    private final BingoScoreboard scoreboard;
    private final TeamManager teamManager;
    private final Map<UUID, Location> deadPlayers;
    private final CardEventManager cardEventManager;
    private final StatisticTracker statTracker;
    private final ConfigData config;
    private GameTimer timer;
    private CountdownTimer startingTimer;
    private boolean hasTimerStarted;

    private BingoTask deathMatchTask;

    public BingoGame(BingoSession session, ConfigData config) {
        this.session = session;
        this.config = config;
        this.worldName = session.worldName;
        this.teamManager = session.teamManager;
        this.scoreboard = session.scoreboard;
        this.settings = session.settingsBuilder.view();
        this.deadPlayers = new HashMap<>();
        this.cardEventManager = new CardEventManager(worldName);
        if (config.disableStatistics)
            this.statTracker = new StatisticTracker(worldName);
        else
            this.statTracker = null;

        start();
    }

    private void start()
    {
        this.hasTimerStarted = false;
        // Create timer
        if (settings.enableCountdown())
            timer = new CountdownTimer(settings.countdownDuration() * 60, 5 * 60, 60, session);
        else
            timer = new CounterTimer();
        timer.setNotifier(time ->
        {
            Message timerMessage = timer.getTimeDisplayMessage(false);
            for (BingoParticipant participant : getTeamManager().getParticipants())
            {
                var p = participant.gamePlayer();
                p.ifPresent(value -> Message.sendActionMessage(timerMessage, value));
            }
            if (statTracker != null)
                statTracker.updateProgress();
        });

        deathMatchTask = null;
        World world = Bukkit.getWorld(getWorldName());
        if (world == null)
        {
            return;
        }
        world.setStorm(false);
        world.setTime(1000);

        // Generate cards
        BingoCard masterCard = CardBuilder.fromMode(settings.mode(), settings.size(), getTeamManager().getActiveTeams().size());
        masterCard.generateCard(settings.card(), settings.seed(), config.disableAdvancements, config.disableStatistics);
        getTeamManager().initializeCards(masterCard);

        Set<BingoCard> cards = new HashSet<>();
        for (BingoTeam activeTeam : getTeamManager().getActiveTeams())
        {
            cards.add(activeTeam.card);
        }
        cardEventManager.setCards(cards.stream().toList());

        if (statTracker != null)
            statTracker.start(getTeamManager().getActiveTeams());

        new TranslatedMessage(BingoTranslation.GIVE_CARDS).sendAll(session);
        teleportPlayersToStart(world);
        getTeamManager().getParticipants().forEach(p ->
        {
            if (p.gamePlayer().isPresent())
            {
                Player player = p.gamePlayer().get();

                ((BingoPlayer)p).giveKit(settings.kit());
                returnCardToPlayer((BingoPlayer) p);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancement revoke " + player.getName() + " everything");
                player.setLevel(0);
                player.setExp(0.0f);
            }
        });

        // Post-start Setup
        scoreboard.reset();

        var event = new BingoStartedEvent(session);
        Bukkit.getPluginManager().callEvent(event);

        // Countdown before the game actually starts
        startingTimer = new CountdownTimer(10, 6, 3, session);
        startingTimer.setNotifier(time -> {
            String timeString = GameTimer.getSecondsString(time);
            if (time == 0)
                timeString = "" + ChatColor.RESET + ChatColor.BOLD + ChatColor.GREEN + "GO";

            ChatColor color = ChatColor.WHITE;
            float pitch;
            if (time <= startingTimer.lowThreshold)
            {
                pitch = 1.414214f;
                color = ChatColor.RED;
            }
            else if (time <= startingTimer.medThreshold)
            {
                pitch = 1.059463f;
                color = ChatColor.GOLD;
            }
            else
            {
                pitch = 0.890899f;
            }

            Message timeDisplay = new Message().untranslated(timeString).bold().color(color);
            teamManager.getParticipants().forEach(p ->
            {
                p.gamePlayer().ifPresent(player -> {
                    Message.sendTitleMessage(timeDisplay, new Message(), player);
                    if (time != 0)
                    {
                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 1.2f - time / 10.0f + 0.2f, pitch);
                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f - time / 10.0f + 0.2f, pitch);
                    }
                });
            });
        });
        BingoReloaded.scheduleTask(task -> {
            startingTimer.start();
        }, 1 * BingoReloaded.ONE_SECOND);
    }

    public void end(@Nullable BingoTeam winningTeam)
    {
        if (statTracker != null)
            statTracker.reset();
        timer.getTimeDisplayMessage(false).sendAll(session);
        timer.stop();

        if (!config.keepScoreboardVisible)
        {
            scoreboard.reset();
        }

        getTeamManager().getParticipants().forEach(p -> {
            if (p instanceof BingoPlayer bingoPlayer)
            {
                bingoPlayer.takeEffects(false);
                p.gamePlayer().ifPresent(player -> {
                    player.playSound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.0f);
                });
            }
        });

        String command = config.sendCommandAfterGameEnded;
        if (!command.equals(""))
        {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }

        var event = new BingoEndedEvent(getGameTime(), winningTeam, session);
        Bukkit.getPluginManager().callEvent(event);
    }

    public void bingo(BingoTeam team)
    {
        new TranslatedMessage(BingoTranslation.BINGO).arg(team.getColoredName().asLegacyString()).sendAll(session);
        for (BingoParticipant p : getTeamManager().getParticipants())
        {
            if (p.gamePlayer().isEmpty())
                continue;

            Player player = p.gamePlayer().get();
            p.gamePlayer().get().playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.75f, 1.0f);

            if (p.getTeam().equals(team))
            {
                BingoReloaded.incrementPlayerStat(player, BingoStatType.WINS);
            }
            else
            {
                BingoReloaded.incrementPlayerStat(player, BingoStatType.LOSSES);
            }
        }
        end(team);
    }

    public long getGameTime()
    {
        if (timer != null)
        {
            return timer.getTime();
        }
        return 0;
    }

    public BingoSettings getSettings()
    {
        return settings;
    }

    public TeamManager getTeamManager()
    {
        return teamManager;
    }

    public void returnCardToPlayer(BingoPlayer participant)
    {
        if (participant.gamePlayer().isEmpty())
            return;

        participant.giveBingoCard();
        participant.gamePlayer().get().setGameMode(GameMode.SURVIVAL);

        BingoReloaded.scheduleTask(task -> participant.giveEffects(settings.effects(), config.gracePeriod), BingoReloaded.ONE_SECOND);
    }

    public void startDeathMatch(int countdown)
    {
        if (countdown == 0)
        {
            deathMatchTask = new BingoTask(new BingoCardsData().getRandomItemTask(settings.card()));

            for (BingoParticipant p : getTeamManager().getParticipants())
            {
                if (p.gamePlayer().isEmpty())
                    continue;

                p.showDeathMatchTask(deathMatchTask);
                Message.sendTitleMessage(
                        "" + ChatColor.BOLD + ChatColor.GOLD + "GO",
                        "" + ChatColor.DARK_PURPLE + ChatColor.ITALIC + "find the item listed in the chat to win!",
                        p.gamePlayer().get());
            }
            return;
        }

        ChatColor color = switch (countdown)
                {
                    case 1 -> ChatColor.RED;
                    case 2 -> ChatColor.GOLD;
                    default -> ChatColor.GREEN;
                };
        for (BingoParticipant p : getTeamManager().getParticipants())
        {
            if (p.gamePlayer().isEmpty())
                continue;

            Message.sendTitleMessage(color + "" + countdown, "", p.gamePlayer().get());
            Message.sendDebug(color + "" + countdown, p.gamePlayer().get());
        }

        BingoReloaded.scheduleTask(task -> {
            startDeathMatch(countdown - 1);
        }, BingoReloaded.ONE_SECOND);
    }

    public void teleportPlayerAfterDeath(Player player)
    {
        if (player == null) return;
        Location location = deadPlayers.get(player.getUniqueId());
        if (location == null)
        {
            new TranslatedMessage(BingoTranslation.EFFECTS_DISABLED).color(ChatColor.RED).send(player);
            return;
        }

        player.teleport(deadPlayers.get(player.getUniqueId()), PlayerTeleportEvent.TeleportCause.PLUGIN);
        deadPlayers.remove(player.getUniqueId());
    }

    public static void spawnPlatform(Location platformLocation, int size)
    {
        for (int x = -size; x < size + 1; x++)
        {
            for (int z = -size; z < size + 1; z++)
            {
                if (!platformLocation.getWorld().getType(
                        (int)platformLocation.getX() + x,
                        (int)platformLocation.getY(),
                        (int)platformLocation.getZ() + z).isSolid())
                {
                    platformLocation.getWorld().setType(
                            (int)platformLocation.getX() + x,
                            (int)platformLocation.getY(),
                            (int)platformLocation.getZ() + z,
                            Material.WHITE_STAINED_GLASS);
                }
            }
        }
    }

    public static void removePlatform(Location platformLocation, int size)
    {
        for (int x = -size; x < size + 1; x++)
        {
            for (int z = -size; z < size + 1; z++)
            {
                if (platformLocation.getWorld().getType(
                        (int)platformLocation.getX() + x,
                        (int)platformLocation.getY(),
                        (int)platformLocation.getZ() + z) == Material.WHITE_STAINED_GLASS)
                {
                    platformLocation.getWorld().setType(
                            (int)platformLocation.getX() + x,
                            (int)platformLocation.getY(),
                            (int)platformLocation.getZ() + z,
                            Material.AIR);
                }
            }
        }
    }

    private void teleportPlayersToStart(World world)
    {
        switch (config.playerTeleportStrategy)
        {
            case ALONE ->
            {
                for (BingoParticipant p : getTeamManager().getParticipants())
                {
                    Location platformLocation = getRandomSpawnLocation(world);
                    teleportPlayerToStart(p, platformLocation);

                    if (getTeamManager().getParticipants().size() > 0)
                    {
                        spawnPlatform(platformLocation.clone(), 5);

                        BingoReloaded.scheduleTask(task ->
                        {
                            BingoGame.removePlatform(platformLocation, 5);
                        }, (long) (Math.max(0, config.gracePeriod - 5)) * BingoReloaded.ONE_SECOND);
                    }
                }
            }
            case TEAM ->
            {
                for (BingoTeam t : getTeamManager().getActiveTeams())
                {
                    Location teamLocation = getRandomSpawnLocation(world);

                    Set<BingoParticipant> players = t.getMembers();
                    players.forEach(p -> teleportPlayerToStart(p, teamLocation));

                    if (players.size() > 0)
                    {
                        spawnPlatform(teamLocation, 5);

                        BingoReloaded.scheduleTask(task ->
                        {
                            BingoGame.removePlatform(teamLocation, 5);
                        }, (long) (Math.max(0, config.gracePeriod - 5)) * BingoReloaded.ONE_SECOND);
                    }
                }
            }
            case ALL ->
            {
                Location spawnLocation = getRandomSpawnLocation(world);
                Set<BingoParticipant> players = getTeamManager().getParticipants();
                players.forEach(p -> teleportPlayerToStart(p, spawnLocation));
                if (getTeamManager().getParticipants().size() > 0)
                {
                    spawnPlatform(spawnLocation, 5);

                    BingoReloaded.scheduleTask(task ->
                    {
                        BingoGame.removePlatform(spawnLocation, 5);
                    }, (long) (Math.max(0, config.gracePeriod - 5)) * BingoReloaded.ONE_SECOND);
                }
            }
            default ->
            {
            }
        }
    }

    private static void teleportPlayerToStart(BingoParticipant participant, Location to)
    {
        if (participant.gamePlayer().isEmpty())
            return;
        Player player = participant.gamePlayer().get();

        Location playerLocation = to.clone();
        playerLocation.setY(playerLocation.getY() + 10.0);
        player.teleport(playerLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.setBedSpawnLocation(to.clone().add(0.0, 2.0, 0.0), true);
    }

    private Location getRandomSpawnLocation(World world)
    {
        Vector position = Vector.getRandom().multiply(config.teleportMaxDistance * 2).subtract(new Vector(config.teleportMaxDistance, config.teleportMaxDistance, config.teleportMaxDistance));
        Location location = new Location(world, position.getX(), world.getHighestBlockYAt(position.getBlockX(), position.getBlockZ()), position.getZ());

        //find a not ocean biome to start the game in
        while (isOceanBiome(world.getBiome(location)))
        {
            position = Vector.getRandom().multiply(config.teleportMaxDistance);
            location = new Location(world, position.getBlockX(), world.getHighestBlockYAt(position.getBlockX(), position.getBlockZ()), position.getBlockZ());
        }

        return location;
    }

    private static boolean isOceanBiome(Biome biome)
    {
        return switch (biome)
                {
                    case OCEAN,
                            DEEP_COLD_OCEAN,
                            COLD_OCEAN,
                            DEEP_OCEAN,
                            FROZEN_OCEAN,
                            DEEP_FROZEN_OCEAN,
                            LUKEWARM_OCEAN,
                            DEEP_LUKEWARM_OCEAN,
                            WARM_OCEAN -> true;
                    default -> false;
                };
    }

    public String getWorldName()
    {
        return worldName;
    }

    public CardEventManager getCardEventManager()
    {
        return cardEventManager;
    }

    public StatisticTracker getStatisticTracker()
    {
        return statTracker;
    }

    public BingoTask getDeathMatchTask()
    {
        return deathMatchTask;
    }

// @EventHandlers ========================================================================

    public void handleBingoTaskComplete(final BingoCardTaskCompleteEvent event)
    {
        String timeString = GameTimer.getTimeAsString(getGameTime());

        new TranslatedMessage(BingoTranslation.COMPLETED).color(ChatColor.AQUA)
                .component(event.getTask().data.getItemDisplayName().asComponent()).color(event.getTask().nameColor)
                .arg(new ItemText(event.getParticipant().getDisplayName(), event.getParticipant().getTeam().getColor().chatColor, ChatColor.BOLD).asLegacyString())
                .arg(timeString).color(ChatColor.WHITE)
                .sendAll(session);

        for (BingoParticipant otherParticipant : getTeamManager().getParticipants())
        {
            if (otherParticipant.gamePlayer().isPresent())
                otherParticipant.gamePlayer().get().playSound(otherParticipant.gamePlayer().get(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.8f, 1.0f);
        }
        if (event.hasBingo())
        {
            bingo(event.getParticipant().getTeam());
        }
        scoreboard.updateTeamScores();

        if (event.getParticipant().gamePlayer().isEmpty())
            return;

        Player player = event.getParticipant().gamePlayer().get();
        BingoReloaded.incrementPlayerStat(player, BingoStatType.TASKS);
    }

    public void handlePlayerDropItem(final PlayerDropItemEvent dropEvent)
    {
        BingoParticipant participant = getTeamManager().getBingoParticipant(dropEvent.getPlayer());
        if (participant == null || participant.gamePlayer().isEmpty())
            return;

        if (PlayerKit.CARD_ITEM.isKeyEqual(dropEvent.getItemDrop().getItemStack()) ||
                PlayerKit.WAND_ITEM.isKeyEqual(dropEvent.getItemDrop().getItemStack()))
        {
            dropEvent.setCancelled(true);
        }
    }

    public void handlePlayerInteract(final PlayerInteractEvent event)
    {
        BingoParticipant participant = getTeamManager().getBingoParticipant(event.getPlayer());
        if (participant == null || participant.gamePlayer().isEmpty())
            return;

        if (event.getItem() == null || event.getItem().getType().isAir())
            return;

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        if (PlayerKit.WAND_ITEM.isKeyEqual(event.getItem()))
        {
            event.setCancelled(true);
            ((BingoPlayer)participant).useGoUpWand(event.getItem(), config.wandCooldown, config.wandDown, config.wandUp, config.platformLifetime);
        }
    }

    public void handleEntityDamage(final EntityDamageEvent event)
    {
        if (!(event.getEntity() instanceof Player p))
            return;

        BingoParticipant participant = getTeamManager().getBingoParticipant(p);
        if (participant == null || participant.gamePlayer().isEmpty())
            return;

        if (!getTeamManager().getParticipants().contains(participant))
            return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;

        if (settings.effects().contains(EffectOptionFlags.NO_FALL_DAMAGE))
        {
            event.setCancelled(true);
        }
    }

    public void handlePlayerDeath(final PlayerDeathEvent event)
    {
        BingoParticipant participant = getTeamManager().getBingoParticipant(event.getEntity());
        if (participant == null || participant.gamePlayer().isEmpty())
            return;

        for (ItemStack drop : event.getDrops())
        {
            if (PDCHelper.getBoolean(drop.getItemMeta().getPersistentDataContainer(), "kit.kit_item", false)
                    || PlayerKit.CARD_ITEM.isKeyEqual(drop))
            {
                drop.setAmount(0);
            }
        }

        Location deathCoords = event.getEntity().getLocation();
        if (config.teleportAfterDeath)
        {
            TextComponent[] teleportMsg = Message.createHoverCommandMessage(BingoTranslation.RESPAWN, "/bingo back");

            event.getEntity().spigot().sendMessage(teleportMsg);
            deadPlayers.put(participant.getId(), deathCoords);
            BingoReloaded.scheduleTask(task -> deadPlayers.remove(participant.getId()), 60 * BingoReloaded.ONE_SECOND);
        }
    }

    public void handlePlayerRespawn(final PlayerRespawnEvent event)
    {
        BingoParticipant participant = getTeamManager().getBingoParticipant(event.getPlayer());
        if (participant == null || participant.gamePlayer().isEmpty())
            return;

        if (!(participant instanceof BingoPlayer player))
            return;

        Message.log("Player " + player.asOnlinePlayer().get().getDisplayName() + " respawned", worldName);

        returnCardToPlayer(player);
        player.giveKit(settings.kit());
    }

    public void handleCountdownFinished(final CountdownTimerFinishedEvent event)
    {
        if (!event.session.phase().equals(this))
            return;

        if (event.getTimer() == timer)
        {
            Set<BingoTeam> tiedTeams = new HashSet<>();
            tiedTeams.add(getTeamManager().getLeadingTeam());

            // Regular bingo cannot draw, so end the game without a winner
            if (settings.mode() == BingoGamemode.REGULAR)
            {
                end(null);
                return;
            }

            int leadingPoints = getTeamManager().getCompleteCount(getTeamManager().getLeadingTeam());
            for (BingoTeam team : getTeamManager().getActiveTeams())
            {
                if (getTeamManager().getCompleteCount(team) == leadingPoints)
                {
                    tiedTeams.add(team);
                }
                else
                {
                    team.outOfTheGame = true;
                }
            }

            // If only 1 team is "tied" for first place, make that team win the game
            if (tiedTeams.size() == 1)
            {
                bingo(getTeamManager().getLeadingTeam());
            }
            else
            {
                startDeathMatch(3);
            }
        }

        else if (event.getTimer() == startingTimer)
        {
            timer.start();
            hasTimerStarted = true;
            teamManager.getParticipants().forEach(p -> p.gamePlayer().ifPresent( gamePlayer -> {
                gamePlayer.playSound(gamePlayer, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1.0f);
                gamePlayer.playSound(gamePlayer, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.0f);
            }));
        }
    }

    public void handlePlayerMove(final PlayerMoveEvent event)
    {
        if (hasTimerStarted)
            return;

        BingoParticipant participant = teamManager.getBingoParticipant(event.getPlayer());
        if (participant == null)
            return;

        Location newLoc = event.getTo();
        newLoc.setX(event.getFrom().getX());
        newLoc.setZ(event.getFrom().getZ());
        event.setTo(newLoc);
    }

    @Override
    public void handleParticipantJoined(BingoParticipantJoinEvent event)
    {
        if (!(event.participant instanceof BingoPlayer player))
            return;

        player.giveEffects(settings.effects(), config.gracePeriod);
    }

    @Override
    public void handleParticipantLeave(BingoParticipantLeaveEvent event)
    {
        if (!(event.participant instanceof BingoPlayer player))
            return;

        deadPlayers.remove(player.getId());
    }

    @Override
    public void handleSettingsUpdated(BingoSettingsUpdatedEvent event)
    {
    }
}