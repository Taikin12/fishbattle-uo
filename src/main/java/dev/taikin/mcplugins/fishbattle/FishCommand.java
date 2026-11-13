package dev.taikin.mcplugins.fishbattle;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class FishCommand {

    private static final String PREFIX = "[魚] ";
    private static final double DEFAULT_STAKE = 100.0;
    private static final List<String> TYPES = List.of("blue", "red", "yellow");

    private final BattleManager battles;
    private final FishMotion motion;
    private final PetMenu menu;

    public FishCommand(BattleManager battles, FishMotion motion, PetMenu menu) {
        this.battles = battles;
        this.motion = motion;
        this.menu = menu;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("uo")
                .requires(source -> source.getSender().hasPermission("uobattle.use"))
                .executes(context -> help(context.getSource().getSender()))
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource().getSender())))
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource().getSender())))
                .then(Commands.literal("battle")
                        .executes(context -> battleNearby(context.getSource().getSender(), DEFAULT_STAKE))
                        .then(Commands.argument("stake", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0))
                                .executes(context -> battleNearby(context.getSource().getSender(),
                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(
                                                context, "stake"))))
                        .then(Commands.literal("stop")
                                .executes(context -> stop(context.getSource().getSender())))
                        .then(Commands.literal("demo")
                                .executes(context -> demo(context.getSource().getSender())))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests(this::suggestPlayers)
                                .executes(context -> battle(context.getSource().getSender(),
                                        StringArgumentType.getString(context, "target"), DEFAULT_STAKE))
                                .then(Commands.argument("stake", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0))
                                        .executes(context -> battle(context.getSource().getSender(),
                                                StringArgumentType.getString(context, "target"),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(
                                                        context, "stake"))))))
                .then(Commands.literal("demo")
                        .executes(context -> demo(context.getSource().getSender())))
                .then(Commands.literal("accept")
                        .executes(context -> accept(context.getSource().getSender())))
                .then(Commands.literal("deny")
                        .executes(context -> deny(context.getSource().getSender())))
                .then(Commands.literal("rps")
                        .then(Commands.argument("hand", StringArgumentType.word())
                                .suggests(this::suggestHands)
                                .executes(context -> rps(context.getSource().getSender(),
                                        StringArgumentType.getString(context, "hand")))))
                .then(Commands.literal("gu").executes(context -> rps(context.getSource().getSender(), "gu")))
                .then(Commands.literal("choki").executes(context -> rps(context.getSource().getSender(), "choki")))
                .then(Commands.literal("pa").executes(context -> rps(context.getSource().getSender(), "pa")))
                .then(Commands.literal("mash")
                        .executes(context -> mash(context.getSource().getSender())))
                .then(Commands.literal("run")
                        .executes(context -> run(context.getSource().getSender())))
                .then(Commands.literal("pet")
                        .then(Commands.literal("rename")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> petRename(context.getSource().getSender(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("follow")
                                .executes(context -> petFollow(context.getSource().getSender())))
                        .then(Commands.literal("collect")
                                .executes(context -> petCollect(context.getSource().getSender())))
                        .then(Commands.literal("remove")
                                .executes(context -> petRemove(context.getSource().getSender())))
                        .then(Commands.literal("train")
                                .executes(context -> petTrain(context.getSource().getSender())))
                        .then(Commands.literal("untrain")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 3))
                                        .executes(context -> petUntrain(context.getSource().getSender(),
                                                IntegerArgumentType.getInteger(context, "slot"))))))
                .then(Commands.literal("bucket")
                        .executes(context -> bucket(context.getSource().getSender(), null))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(source -> source.getSender().hasPermission("uobattle.admin"))
                                .suggests(this::suggestPlayers)
                                .executes(context -> bucket(context.getSource().getSender(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("card")
                        .requires(source -> source.getSender().hasPermission("uobattle.admin"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(this::suggestPlayers)
                                .executes(context -> card(context.getSource().getSender(),
                                        StringArgumentType.getString(context, "player"), null))
                                .then(Commands.argument("technique", StringArgumentType.word())
                                        .suggests(this::suggestTechniques)
                                        .executes(context -> card(context.getSource().getSender(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "technique"))))))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource().getSender(), false))
                        .then(Commands.literal("all")
                                .requires(source -> source.getSender().hasPermission("uobattle.admin"))
                                .executes(context -> clear(context.getSource().getSender(), true))))
                .then(Commands.literal("give")
                        .requires(source -> source.getSender().hasPermission("uobattle.admin"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(this::suggestPlayers)
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(this::suggestTypes)
                                        .executes(context -> give(context.getSource().getSender(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "type"), 5))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, Fish.MAX_LEVEL))
                                                .executes(context -> give(context.getSource().getSender(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "type"),
                                                        IntegerArgumentType.getInteger(context, "level")))))))
                .build();
    }

    private int info(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ使用できます。", NamedTextColor.RED));
            return 0;
        }
        Fish fish = FishItems.read(player.getInventory().getItemInMainHand());
        if (fish == null) {
            fish = FishInventory.find(player);
        }
        if (fish == null) {
            player.sendMessage(Component.text(PREFIX + "バケツに入った魚を持っていません。", NamedTextColor.YELLOW));
            return 0;
        }
        player.sendMessage(Component.text(PREFIX + "個体情報", NamedTextColor.AQUA));
        player.sendMessage(FishItems.name(fish));
        for (Component line : FishItems.lore(fish)) {
            player.sendMessage(line);
        }
        FishType type = fish.type();
        player.sendMessage(Component.text("属性: " + type.label() + "（" + type.beats().label()
                + " に強い）", NamedTextColor.GRAY));
        return 1;
    }

    private int battleNearby(org.bukkit.command.CommandSender sender, double stake) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ使用できます。", NamedTextColor.RED));
            return 0;
        }
        battles.challengeNearby(player, stake);
        return 1;
    }

    private int battle(org.bukkit.command.CommandSender sender, String targetName, double stake) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ使用できます。", NamedTextColor.RED));
            return 0;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(Component.text(PREFIX + targetName + " はオンラインではありません。", NamedTextColor.RED));
            return 0;
        }
        if (target.equals(player)) {
            player.sendMessage(Component.text(PREFIX + "自分とは戦えません。", NamedTextColor.RED));
            return 0;
        }
        battles.challenge(player, target, stake);
        return 1;
    }

    private int stop(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ使用できます。", NamedTextColor.RED));
            return 0;
        }
        if (!battles.inBattle(player.getUniqueId())) {
            player.sendMessage(Component.text(PREFIX + "バトル中ではありません。", NamedTextColor.GRAY));
            return 0;
        }
        battles.abort(player);
        return 1;
    }

    private int demo(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ使用できます。", NamedTextColor.RED));
            return 0;
        }
        battles.startDemo(player);
        return 1;
    }

    private int accept(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        battles.accept(player);
        return 1;
    }

    private int deny(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        battles.deny(player);
        return 1;
    }

    private int help(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(Component.text(PREFIX + "コマンド一覧", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/uo battle [賭け金]  近くの人に対戦を申し込む（既定 100）",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo battle <player> [賭け金]  相手を指定して申し込む",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo demo             NPCとデモバトル", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo battle demo      NPCとデモバトル", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo battle stop      バトルを降参して終了", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo accept | deny    申し込みを受ける / 断る", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo gu | choki | pa  じゃんけんの手を選ぶ", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo mash             攻撃チャンスで連打", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo run              降参", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo info            個体情報", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo pet train       装備中の技を確認・外す", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo bucket [player]  捕獲バケツを入手", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo card <player> [技]  技カードを配布（管理者）", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/uo clear [all]      出した魚を消す（all は管理者）", NamedTextColor.GRAY));
        return 1;
    }

    private int rps(org.bukkit.command.CommandSender sender, String hand) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        Rps choice = switch (hand.toLowerCase(Locale.ROOT)) {
            case "gu", "g", "rock" -> Rps.GU;
            case "choki", "c", "scissors" -> Rps.CHOKI;
            case "pa", "p", "paper" -> Rps.PA;
            default -> null;
        };
        if (choice == null) {
            player.sendMessage(Component.text(PREFIX + "gu / choki / pa のいずれかです。", NamedTextColor.RED));
            return 0;
        }
        battles.preview(player, choice);
        return 1;
    }

    private int mash(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        battles.mash(player);
        return 1;
    }

    private int run(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        battles.forfeit(player);
        return 1;
    }

    private int petRename(org.bukkit.command.CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        menu.rename(player, name);
        return 1;
    }

    private int petFollow(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        menu.toggleFollow(player);
        return 1;
    }

    private int petCollect(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        menu.collect(player);
        return 1;
    }

    private int petTrain(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        menu.listTechniques(player);
        return 1;
    }

    private int petUntrain(org.bukkit.command.CommandSender sender, int slot) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        menu.untrain(player, slot);
        return 1;
    }

    private int bucket(org.bukkit.command.CommandSender sender, String targetName) {
        Player target;
        if (targetName == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("コンソールからは /uo bucket <player> を使ってください。",
                        NamedTextColor.RED));
                return 0;
            }
            target = player;
        } else {
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage(Component.text(PREFIX + targetName + " はオンラインではありません。",
                        NamedTextColor.RED));
                return 0;
            }
        }
        var leftovers = target.getInventory().addItem(FishItems.captureBucket());
        if (!leftovers.isEmpty()) {
            sender.sendMessage(Component.text(PREFIX + target.getName()
                    + " のインベントリに空きがありません。", NamedTextColor.RED));
            return 0;
        }
        target.sendMessage(Component.text(PREFIX + "捕獲バケツを受け取りました。", NamedTextColor.GREEN));
        if (!target.equals(sender)) {
            sender.sendMessage(Component.text(PREFIX + target.getName() + " に捕獲バケツを渡しました。",
                    NamedTextColor.GREEN));
        }
        return 1;
    }

    private int card(org.bukkit.command.CommandSender sender, String targetName, String techniqueId) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(Component.text(PREFIX + targetName + " はオンラインではありません。", NamedTextColor.RED));
            return 0;
        }
        ItemStack item;
        if (techniqueId == null) {
            item = TechniqueItem.random();
        } else {
            Technique technique = Technique.byId(techniqueId);
            if (technique == null) {
                sender.sendMessage(Component.text(PREFIX + "そんな技はありません。", NamedTextColor.RED));
                return 0;
            }
            item = TechniqueItem.create(technique);
        }
        target.getInventory().addItem(item);
        target.sendMessage(Component.text(PREFIX + "技カードを受け取りました。", NamedTextColor.GREEN));
        sender.sendMessage(Component.text(PREFIX + target.getName() + " に技カードを渡しました。",
                NamedTextColor.GREEN));
        return 1;
    }

    private CompletableFuture<Suggestions> suggestTechniques(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (Technique technique : Technique.all()) {
            if (technique.id().startsWith(remaining)) {
                builder.suggest(technique.id());
            }
        }
        return builder.buildFuture();
    }

    private int petRemove(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return 0;
        }
        menu.remove(player);
        return 1;
    }

    private int clear(org.bukkit.command.CommandSender sender, boolean all) {
        Player player = sender instanceof Player p ? p : null;
        if (player == null && !all) {
            sender.sendMessage(Component.text("コンソールからは /uo clear all を使ってください。", NamedTextColor.RED));
            return 0;
        }
        int removed = motion.clear(all ? null : player.getUniqueId());
        if (removed == 0) {
            sender.sendMessage(Component.text(PREFIX + "消す魚はいません。", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text(PREFIX + removed + " 匹の出した魚を消しました。", NamedTextColor.GREEN));
        }
        return removed;
    }

    private int give(org.bukkit.command.CommandSender sender, String targetName, String typeName, int level) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(Component.text(PREFIX + targetName + " はオンラインではありません。", NamedTextColor.RED));
            return 0;
        }
        FishType type;
        try {
            type = FishType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(PREFIX + "タイプは " + String.join(", ", TYPES) + " のいずれかです。",
                    NamedTextColor.RED));
            return 0;
        }
        ItemStack fish = FishItems.randomBucket(type, level);
        target.getInventory().addItem(fish);
        target.sendMessage(Component.text(PREFIX + "魚を受け取りました。", NamedTextColor.GREEN));
        sender.sendMessage(Component.text(PREFIX + target.getName() + " に " + type.label() + " 属性 Lv" + level
                + " の魚を渡しました。", NamedTextColor.GREEN));
        return 1;
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestTypes(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String type : TYPES) {
            if (type.startsWith(remaining)) {
                builder.suggest(type);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestHands(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String hand : List.of("gu", "choki", "pa")) {
            if (hand.startsWith(remaining)) {
                builder.suggest(hand);
            }
        }
        return builder.buildFuture();
    }
}
