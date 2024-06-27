package io.github.steaf23.bingoreloaded.gui.inventory;

import io.github.steaf23.bingoreloaded.data.BingoTranslation;
import io.github.steaf23.bingoreloaded.data.TeamData;
import io.github.steaf23.easymenulib.inventory.*;
import io.github.steaf23.easymenulib.inventory.item.ItemTemplate;
import io.github.steaf23.easymenulib.inventory.item.action.NameEditAction;
import io.github.steaf23.easymenulib.util.ChatComponentUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TeamEditorMenu extends PaginatedSelectionMenu
{
    private final TeamData teamData;

    private static final TeamData.TeamTemplate DEFAULT_NEW_TEAM = new TeamData.TeamTemplate("MyTeam", TextColor.fromHexString("#808080"));

    private static final ItemTemplate RESTORE_DEFAULT = new ItemTemplate(2, 5, Material.TNT,
            Component.text("Restore Default Teams").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
            Component.text("This option will remove all created teams!"));

    private static final ItemTemplate CREATE_TEAM = new ItemTemplate(6, 5, Material.EMERALD,
            Component.text("Create New Team").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));

    public TeamEditorMenu(MenuBoard manager) {
        super(manager, "Edit Teams", new ArrayList<>(), FilterType.DISPLAY_NAME);
        this.teamData = new TeamData();

        addAction(RESTORE_DEFAULT, p -> {
            teamData.reset();
            updateDisplay();
        });
        addAction(CREATE_TEAM, p -> createTeamEditor("").open(p));
    }

    public void updateDisplay() {
        clearFilter();
        clearItems();
        List<ItemTemplate> items = new ArrayList<>();

        var teamMap = teamData.getTeams();
        for (String key : teamMap.keySet()) {
            TeamData.TeamTemplate template = teamMap.get(key);
            items.add(ItemTemplate.createColoredLeather(template.color(), Material.LEATHER_HELMET)
                    .setName(Component.text(template.name()).color(template.color()).decorate(TextDecoration.BOLD))
                    .setLore(Component.text("id: ").append(Component.text(key).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)))
                    .setCompareKey(key));
        }
        addItemsToSelect(items);
    }

    public BasicMenu createTeamEditor(@NotNull String teamKey) {
        return new TeamEdit(getMenuBoard(), teamData.getTeam(teamKey, DEFAULT_NEW_TEAM), editedTemplate -> teamData.addTeam(teamKey, editedTemplate));
    }

    @Override
    public void onOptionClickedDelegate(InventoryClickEvent event, ItemTemplate clickedOption, HumanEntity player) {
        String key = clickedOption.getCompareKey();
        if (event.getClick() == ClickType.RIGHT) {
            teamData.removeTeam(key);
            updateDisplay();
        } else {
            createTeamEditor(key).open(player);
        }
    }

    @Override
    public void beforeOpening(HumanEntity player) {
        updateDisplay();
        super.beforeOpening(player);
    }

    static class TeamEdit extends BasicMenu
    {
        private final Consumer<TeamData.TeamTemplate> finishedCallback;
        private TeamData.TeamTemplate templateToEdit;

        public TeamEdit(MenuBoard manager, TeamData.TeamTemplate teamToEdit, Consumer<TeamData.TeamTemplate> callback) {
            super(manager, "Edit team", 3);
            this.templateToEdit = teamToEdit;
            this.finishedCallback = callback;

            // Change the team name
            ItemTemplate teamNameItem = new ItemTemplate(2, 1, Material.WRITABLE_BOOK, Component.text(templateToEdit.name()));
            teamNameItem.setAction(new NameEditAction("Edit team name", getMenuBoard(), (value, item) -> {
                templateToEdit = new TeamData.TeamTemplate(value, templateToEdit.color());
                //TODO: find a way to do addItem(teamNameItem); automatically??
                addItem(item);
            }));

            addItem(teamNameItem);

            // Add action to change the team's color.
            ItemTemplate teamColorItem = new ItemTemplate(4, 1, Material.LEATHER_CHESTPLATE, Component.text("Color").color(templateToEdit.color()).decorate(TextDecoration.BOLD))
                    .setLeatherColor(templateToEdit.color());

            // TODO: maybe find a less cursed way to fix this?
            addAction(teamColorItem, args -> {
                new ColorPickerMenu(getMenuBoard(), "Pick team color", (result) -> {
                    // Update template
                    templateToEdit = new TeamData.TeamTemplate(templateToEdit.name(), result);

                    // Update menu item
                    teamColorItem.setLeatherColor(templateToEdit.color())
                            .setName(Component.text("Color").color(templateToEdit.color()).decorate(TextDecoration.BOLD));
                    this.addItem(teamColorItem);
                }).open(args);
            });

            addCloseAction(new ItemTemplate(6, 1, Material.BARRIER,
                    Component.text(BingoTranslation.MENU_EXIT.translate()).color(NamedTextColor.RED).decorate(TextDecoration.BOLD)));
        }

        @Override
        public void beforeClosing(HumanEntity player) {
            super.beforeClosing(player);
            finishedCallback.accept(templateToEdit);
        }
    }
}
