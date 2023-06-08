package io.github.steaf23.bingoreloaded.gameloop.singular;

import io.github.steaf23.bingoreloaded.BingoReloaded;
import io.github.steaf23.bingoreloaded.data.ConfigData;
import io.github.steaf23.bingoreloaded.event.BingoEventListener;
import io.github.steaf23.bingoreloaded.gameloop.BingoGameManager;
import io.github.steaf23.bingoreloaded.gameloop.BingoSession;
import io.github.steaf23.bingoreloaded.gui.base.MenuEventListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class SingularGameManager implements BingoGameManager
{
    private final BingoEventListener eventListener;
    private final MenuEventListener menuListener;
    private final BingoSession session;

    public SingularGameManager(BingoReloaded plugin)
    {
        ConfigData config = plugin.config();
        this.session = new BingoSession(config.defaultWorldName, config);

        this.eventListener = new BingoEventListener(world ->
                BingoReloaded.getWorldNameOfDimension(world).equals(session.worldName) ? session : null
                , config.disableAdvancements, config.disableStatistics);

        this.menuListener = new MenuEventListener(inventoryView -> {
            String worldName = BingoReloaded.getWorldNameOfDimension(inventoryView.getPlayer().getWorld());
            return worldName.equals(session.worldName);
        });

        plugin.registerCommand("bingobot", new BotCommand(session.teamManager), null);

        Bukkit.getPluginManager().registerEvents(eventListener, plugin);
        Bukkit.getPluginManager().registerEvents(menuListener, plugin);
    }

    @Override
    public BingoSession getSession(Player player)
    {
        return session;
    }

    @Override
    public void onDisable()
    {
        HandlerList.unregisterAll(eventListener);
        HandlerList.unregisterAll(menuListener);
    }
}