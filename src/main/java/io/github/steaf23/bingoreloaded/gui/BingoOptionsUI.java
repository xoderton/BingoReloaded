package io.github.steaf23.bingoreloaded.gui;

import io.github.steaf23.bingoreloaded.BingoGame;
import io.github.steaf23.bingoreloaded.data.BingoCardsData;
import io.github.steaf23.bingoreloaded.MessageSender;
import io.github.steaf23.bingoreloaded.item.InventoryItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BingoOptionsUI extends AbstractGUIInventory
{
    private final BingoGame game;

    private final InventoryItem start = new InventoryItem(GUIBuilder5x9.OptionPositions.SEVEN_CENTER1.positions[3],
            Material.LIME_CONCRETE, TITLE_PREFIX + "Start The Game");

    private static final InventoryItem JOIN = new InventoryItem(GUIBuilder5x9.OptionPositions.SEVEN_CENTER1.positions[0],
            Material.WHITE_GLAZED_TERRACOTTA, TITLE_PREFIX + "Join A Team");
    private static final InventoryItem LEAVE = new InventoryItem(GUIBuilder5x9.OptionPositions.SEVEN_CENTER1.positions[1],
            Material.BARRIER, TITLE_PREFIX + "Quit Bingo");
    private static final InventoryItem KIT = new InventoryItem(GUIBuilder5x9.OptionPositions.SEVEN_CENTER1.positions[2],
            Material.IRON_INGOT, TITLE_PREFIX + "Change Starting Kit");
    private static final InventoryItem CARD = new InventoryItem(GUIBuilder5x9.OptionPositions.SEVEN_CENTER1.positions[4],
            Material.MAP, TITLE_PREFIX + "Change Bingo Card");
    private static final InventoryItem MODE = new InventoryItem(GUIBuilder5x9.OptionPositions.SEVEN_CENTER1.positions[5],
            Material.ENCHANTED_BOOK, TITLE_PREFIX + "Change Gamemode");
    private static final InventoryItem EFFECTS = new InventoryItem(GUIBuilder5x9.OptionPositions.SEVEN_CENTER1.positions[6],
            Material.POTION, TITLE_PREFIX + "Change Player Effects");

    private static final InventoryItem JOIN_P = JOIN.inSlot(GUIBuilder5x9.OptionPositions.TWO_HORIZONTAL_WIDE.positions[0]);
    private static final InventoryItem LEAVE_P = LEAVE.inSlot(GUIBuilder5x9.OptionPositions.TWO_HORIZONTAL_WIDE.positions[1]);

    @Override
    public void delegateClick(InventoryClickEvent event, int slotClicked, Player player, ClickType clickType)
    {
        if (!player.hasPermission("bingo.settings"))
        {
            if (slotClicked == JOIN_P.getSlot())
            {
                game.getTeamManager().openTeamSelector(player, this);
            }
            else if (slotClicked == LEAVE_P.getSlot())
            {
                game.getTeamManager().playerQuit(player);
            }
            return;
        }


        if (slotClicked == JOIN.getSlot())
        {
            game.getTeamManager().openTeamSelector(player, this);
        }
        else if (slotClicked == LEAVE.getSlot())
        {
            game.getTeamManager().playerQuit(player);
        }
        else if (slotClicked == KIT.getSlot())
        {
            KitOptionsUI kitSelector = new KitOptionsUI(this, game);
            kitSelector.open(player);
        }
        else if (slotClicked == MODE.getSlot())
        {
            GamemodeOptionsUI gamemodeSelector = new GamemodeOptionsUI(this, game);
            gamemodeSelector.open(player);
        }
        else if (slotClicked == CARD.getSlot())
        {
            openCardPicker(player);
        }
        else if (slotClicked == EFFECTS.getSlot())
        {
            EffectOptionsUI effectSelector = new EffectOptionsUI(this, game);
            effectSelector.open(player);
        }
        else if (slotClicked == start.getSlot())
        {
            if (game.inProgress)
            {
                game.end();
                start.setType(Material.LIME_CONCRETE);
                ItemMeta meta = start.getItemMeta();
                if (meta != null)
                {
                    meta.setDisplayName(TITLE_PREFIX + "Start The Game");
                    start.setItemMeta(meta);
                }
                fillOptions(new InventoryItem[]{start});
            }
            else
            {
                game.start();
            }
        }
    }

    public static void open(Player player, BingoGame gameInstance)
    {
        BingoOptionsUI options = new BingoOptionsUI(gameInstance);
        if (gameInstance.inProgress)
        {
            options.start.setType(Material.RED_CONCRETE);
            ItemMeta meta = options.start.getItemMeta();
            if (meta != null)
            {
                meta.setDisplayName(TITLE_PREFIX + "End The Game");
                options.start.setItemMeta(meta);
            }
        }
        else
        {
            options.start.setType(Material.LIME_CONCRETE);
            ItemMeta meta = options.start.getItemMeta();
            if (meta != null)
            {
                meta.setDisplayName(TITLE_PREFIX + "Start The Game");
                options.start.setItemMeta(meta);
            }
        }
        if (player.hasPermission("bingo.settings"))
        {
            options.fillOptions(new InventoryItem[]{
                    JOIN,
                    LEAVE,
                    KIT,
                    MODE,
                    CARD,
                    options.start,
                    EFFECTS,
            });
        }
        else if (player.hasPermission("bingo.player"))
        {
            options.fillOptions(new InventoryItem[]{
                    JOIN_P.inSlot(20),
                    LEAVE_P.inSlot(24),
            });
        }
        options.open(player);
    }

    private BingoOptionsUI(BingoGame game)
    {
        super(45, "Options Menu", null);
        this.game = game;
    }

    private void openCardPicker(Player player)
    {
        List<InventoryItem> cards = new ArrayList<>();

        for (String cardName : BingoCardsData.getCardNames())
        {
            cards.add(new InventoryItem(Material.PAPER, cardName,
                    ChatColor.DARK_PURPLE + "Contains " +
                            BingoCardsData.getLists(cardName).size() + " item List(s)"));
        }

        ListPickerUI cardPicker = new ListPickerUI(cards, "Pick a card",this, FilterType.DISPLAY_NAME)
        {
            @Override
            public void onOptionClickedDelegate(InventoryClickEvent event, InventoryItem clickedOption, Player player)
            {
                ItemMeta meta = clickedOption.getItemMeta();
                if (meta != null)
                {
                    cardSelected(meta.getDisplayName());
                }
                close(player);
            }
        };
        cardPicker.open(player);
    }

    private void cardSelected(String cardName)
    {
        if (cardName == null) return;
        MessageSender.sendAll("game.start.card", cardName);
        game.getSettings().card = cardName;
    }
}