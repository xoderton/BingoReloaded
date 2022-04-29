package me.steven.bingoreloaded.item;

import me.steven.bingoreloaded.BingoReloaded;
import me.steven.bingoreloaded.GameTimer;
import me.steven.bingoreloaded.gui.cards.CardBuilder;
import org.apache.commons.lang.WordUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class BingoItem
{
    public final Material item;
    public final InventoryItem stack;
    public final String name;

    public BingoItem(Material item)
    {
        this.item = item;
        this.stack = new InventoryItem(item, null, "I am a bingo Item :D");
        this.name = convertToReadableName(item);
    }

    public static String convertToReadableName(Material m)
    {
        String name = m.name().replace("_", " ");
        return WordUtils.capitalizeFully(name);
    }

    public boolean isComplete(Team team)
    {
        if (completedBy == null) return false;

        return completedBy.getDisplayName().equals(team.getDisplayName());
    }

    public void complete(Team team, int time)
    {
        if (completedBy != null) return;

        Material completeMaterial = CardBuilder.completeColor(team);

        completedBy = team;

        String timeString = GameTimer.getTimeAsString(time);

        BingoReloaded.broadcast(ChatColor.GREEN + "Completed " + name + " by team " + completedBy.getColor() + completedBy.getDisplayName() + ChatColor.GREEN + "! At " + timeString);

        String crossedName = "" + ChatColor.GRAY + ChatColor.STRIKETHROUGH + name;
        stack.setType(completeMaterial);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null)
        {
            meta.setDisplayName(crossedName);
            meta.setLore(List.of("Completed by team " + completedBy.getColor() + completedBy.getDisplayName(),
                    "At " + ChatColor.GOLD + timeString + ChatColor.RESET + ""));
            stack.setItemMeta(meta);
        }
    }

    public Team getWhoCompleted()
    {
        return completedBy;
    }

    public void voidItem()
    {
        stack.setType(Material.BEDROCK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null)
        {
            meta.setDisplayName("" + ChatColor.BLACK + ChatColor.STRIKETHROUGH + name);
            meta.setLore(List.of(ChatColor.BLACK + "This team is out of the game!"));
            stack.setItemMeta(meta);
        }
    }

    private Team completedBy = null;
}
