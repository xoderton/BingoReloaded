package io.github.steaf23.bingoreloaded.gui.inventory.card;

import io.github.steaf23.bingoreloaded.cards.CardSize;
import io.github.steaf23.bingoreloaded.data.BingoMessage;
import io.github.steaf23.bingoreloaded.gui.inventory.CardMenu;
import io.github.steaf23.bingoreloaded.settings.BingoGamemode;
import io.github.steaf23.bingoreloaded.tasks.GameTask;
import io.github.steaf23.playerdisplay.inventory.BasicMenu;
import io.github.steaf23.playerdisplay.inventory.MenuBoard;
import io.github.steaf23.playerdisplay.inventory.item.ItemTemplate;
import io.github.steaf23.playerdisplay.util.MultilineComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GenericCardMenu extends BasicMenu implements CardMenu
{
    protected final CardSize size;
    protected final BingoGamemode mode;
    protected List<GameTask> tasks;

    public GenericCardMenu(MenuBoard menuBoard, BingoGamemode mode, CardSize cardSize)
    {
        super(menuBoard, BingoMessage.CARD_TITLE.asPhrase(), cardSize.size);
        this.size = cardSize;
        this.mode = mode;
        this.tasks = new ArrayList<>();
        setMaxStackSizeOverride(64);
    }

    public void updateTasks(List<GameTask> tasks) {
        this.tasks = tasks;
        for (int i = 0; i < tasks.size(); i++)
        {
            addItem(getItemFromTask(i).setSlot(size.getCardInventorySlot(i)));
        }
    }

    @Override
    public CardMenu copy() {
        return new GenericCardMenu(getMenuBoard(), mode, size);
    }

    public @NotNull ItemTemplate getItemFromTask(int taskIndex) {
        ItemTemplate item = tasks.get(taskIndex).toItem();
        return item;
    }

    public void setInfo(Component name, Component... description)
    {
        ItemTemplate info = new ItemTemplate(0, Material.MAP,
                name.decorate(TextDecoration.BOLD).color(mode.getColor()),
                MultilineComponent.from(NamedTextColor.YELLOW, TextDecoration.ITALIC, description));
        addItem(info);
    }

    @Override
    public void beforeOpening(HumanEntity player) {
        for (int i = 0; i < tasks.size(); i++)
        {
            addItem(getItemFromTask(i).setSlot(size.getCardInventorySlot(i)));
        }
    }
}
