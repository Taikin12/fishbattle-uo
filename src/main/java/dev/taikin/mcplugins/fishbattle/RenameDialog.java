package dev.taikin.mcplugins.fishbattle;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

final class RenameDialog {

    private static final String INPUT_KEY = "nick";

    private final FishMotion motion;

    RenameDialog(FishMotion motion) {
        this.motion = motion;
    }

    void open(Player player, Entity entity) {
        Fish fish = FishData.read(entity);
        if (fish == null) {
            return;
        }
        UUID entityId = entity.getUniqueId();
        String current = fish.nick() == null ? "" : fish.nick();

        ClickCallback.Options options = ClickCallback.Options.builder()
                .uses(8)
                .lifetime(Duration.ofMinutes(2))
                .build();

        ActionButton confirm = ActionButton.builder(Component.text("決定", NamedTextColor.GREEN))
                .tooltip(Component.text("この名前で保存します", NamedTextColor.GRAY))
                .width(100)
                .action(DialogAction.customClick((DialogResponseView response,
                        net.kyori.adventure.audience.Audience audience) -> {
                    String name = response.getText(INPUT_KEY);
                    if (!(audience instanceof Player viewer)) {
                        return;
                    }
                    Entity target = org.bukkit.Bukkit.getEntity(entityId);
                    if (target == null || !target.isValid()) {
                        viewer.sendMessage(Component.text("魚が見つかりません。", NamedTextColor.RED));
                        return;
                    }
                    apply(viewer, target, name);
                    viewer.closeDialog();
                }, options))
                .build();

        ActionButton cancel = ActionButton.builder(Component.text("やめる", NamedTextColor.RED))
                .width(100)
                .action(DialogAction.customClick((DialogResponseView response,
                        net.kyori.adventure.audience.Audience audience) -> {
                    if (audience instanceof Player viewer) {
                        viewer.closeDialog();
                    }
                }, options))
                .build();

        TextDialogInput input = DialogInput.text(INPUT_KEY,
                        Component.text("新しい名前", NamedTextColor.AQUA))
                .initial(current)
                .maxLength(24)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("名前を変更", NamedTextColor.DARK_AQUA))
                        .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                                Component.text("魚の新しい名前を入力してください（24文字まで）",
                                        NamedTextColor.GRAY), 300)))
                        .inputs(List.of(input))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.confirmation(confirm, cancel)));
        player.showDialog(dialog);
    }

    void apply(Player player, Entity entity, String name) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.length() > 24) {
            cleaned = cleaned.substring(0, 24);
        }
        entity.getPersistentDataContainer().set(FishKeys.NICK, PersistentDataType.STRING, cleaned);
        Fish fish = FishData.read(entity);
        if (fish != null) {
            entity.customName(FishItems.name(fish));
            entity.setCustomNameVisible(true);
        }
        player.sendMessage(Component.text("名前を「" + cleaned + "」にしました。", NamedTextColor.GREEN));
    }
}
