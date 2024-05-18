package io.github.steaf23.easymenulib.menu.item.action;


import io.github.steaf23.easymenulib.menu.BasicMenu;
import io.github.steaf23.easymenulib.menu.MenuBoard;
import io.github.steaf23.easymenulib.menu.UserInputMenu;
import io.github.steaf23.easymenulib.menu.item.ItemTemplate;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.HumanEntity;

import java.util.function.BiConsumer;

public class NameEditAction extends MenuAction
{
    private String value;
    private final BiConsumer<String, ItemTemplate> callback;
    private final MenuBoard board;
    private final String prompt;

    public NameEditAction(String prompt, MenuBoard board, BiConsumer<String, ItemTemplate> callback) {
        this.callback = callback;
        this.board = board;
        this.prompt = prompt;
        this.value = "";
    }

    @Override
    public void use(BasicMenu.ActionArguments arguments) {
        renameItem("", arguments.player());
    }

    protected void renameItem(String format, HumanEntity player) {
        new UserInputMenu(board, prompt, (result) -> {
            value = result;
            item.setName(TextComponent.fromLegacy(format + value));
            callback.accept(value, item);
        }, player, value);
    }
}
