package io.github.steaf23.playerdisplay.inventory.item.action;


import io.github.steaf23.playerdisplay.inventory.BasicMenu;
import io.github.steaf23.playerdisplay.inventory.MenuBoard;
import io.github.steaf23.playerdisplay.inventory.UserInputMenu;
import io.github.steaf23.playerdisplay.inventory.item.ItemTemplate;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.HumanEntity;

import java.util.function.BiConsumer;

public class NameEditAction extends MenuAction
{
    private String value;
    private final BiConsumer<String, ItemTemplate> callback;
    private final MenuBoard board;
    private final Component prompt;

    public NameEditAction(Component prompt, MenuBoard board, BiConsumer<String, ItemTemplate> callback) {
        this.callback = callback;
        this.board = board;
        this.prompt = prompt;
        this.value = "";
    }

    @Override
    public void use(ActionArguments arguments) {
        renameItem(arguments.player());
    }

    protected void renameItem(HumanEntity player) {
        new UserInputMenu(board, prompt, (result) -> {
            value = result;
            item.setName(Component.text(value));
            callback.accept(value, item);
        }, value)
                .open(player);
    }
}
