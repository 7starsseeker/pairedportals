package com.example.pairedportals.text;

import java.util.UUID;

import com.example.pairedportals.compat.WorldsCompat;
import com.example.pairedportals.portal.ActivationPolicy;

/**
 * Every string a player can read, in both languages, in one file.
 *
 * <p>Collected here rather than left at their call sites for the reason the call sites are spread out:
 * the action-bar hints live in {@code visual/FrameNameHint}, the ignition refusals in
 * {@code mixin/BaseFireBlockMixin}, the whole operator surface in {@code command/CustomPortalCommand}.
 * A translation that has to be hunted down per call site is a translation that goes stale one string at
 * a time; keeping the columns side by side makes "did this line get both?" answerable by looking, and
 * makes the wording of two related messages easy to keep consistent.
 *
 * <p>What is <em>not</em> here: server log lines, {@code -Dpairedportals.debug=true} output, and the
 * technical replay printed by {@code /pairedportals debug}. Those are read by whoever is operating the
 * server, in a language the server operators here already use, and {@code test/smoke-*.sh} greps them —
 * so they are English, always, and deliberately not player-facing. Nor is the enumeration of a door
 * state ({@code BOUND} / {@code PENDING}), which is a stored token that also appears in the saved data,
 * not prose.
 */
public final class Messages {
    private Messages() {
    }

    // ---------------------------------------------------------------------------------------------
    // Who may light a door
    // ---------------------------------------------------------------------------------------------

    /** The activation policy as a phrase: "everyone", "operators only", "nobody". */
    public static Text policy(ActivationPolicy policy) {
        return switch (policy) {
            case ALL -> Text.of("everyone", "所有人");
            case OP -> Text.of("operators only", "仅 OP");
            case NONE -> Text.of("nobody", "所有人都不行");
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Where the other end stands
    // ---------------------------------------------------------------------------------------------

    /** The state of a twin's world, as a phrase. */
    public static Text worldState(WorldsCompat.WorldState state) {
        return switch (state) {
            case LOADED -> Text.of("loaded", "已加载");
            case UNLOADED -> Text.of("registered, not loaded", "未加载（用的时候会自动加载）");
            case UNKNOWN -> Text.of("no such world", "世界不存在");
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Action-bar hints — what a door says about itself when a player looks at it
    // ---------------------------------------------------------------------------------------------

    /** Aiming at a lit door that is still waiting for a partner with the same sign words. */
    public static Text hintWaiting(String signText) {
        return Text.of("Unbound custom portal: waiting for another door carrying \"" + signText + "\"",
                "未绑定的自定义传送门：等待另一扇写着「" + signText + "」的门");
    }

    /** Aiming at a bound door: where its twin stands, and whether that world is up. */
    public static Text hintBound(String dimension, String pos, WorldsCompat.WorldState state) {
        return Text.of("Bound custom portal: leads to " + dimension + " " + pos + " ("
                        + worldState(state).resolve(Lang.EN) + ")",
                "已绑定的自定义传送门：通往 " + dimension + " " + pos + "（"
                        + worldState(state).resolve(Lang.ZH) + "）");
    }

    /** Aiming at a bound door whose twin record is gone — the one state the world cannot show. */
    public static Text hintTwinGone() {
        return Text.of("Bound custom portal (its twin's record is gone)",
                "已绑定的自定义传送门（对门记录已不在）");
    }

    // ---------------------------------------------------------------------------------------------
    // Ignition refusals — fire was placed, but no door came of it
    // ---------------------------------------------------------------------------------------------

    /** A frame-shaped hole with one sign in it, and the sign has no text. */
    public static Text signBlank() {
        return Text.of("the sign in the frame is blank", "门洞内的告示牌没有内容");
    }

    /** A frame-shaped hole with more than one written sign in it. */
    public static Text signTooMany() {
        return Text.of("only one written sign fits in the frame", "门洞内只能放一块有内容的告示牌");
    }

    /** The server's activation policy refused the ignition; the policy is named so it can be acted on. */
    public static Text noPermission(ActivationPolicy policy) {
        return Text.of("no permission to light a custom portal (currently: "
                        + policy(policy).resolve(Lang.EN) + ")",
                "没有权限激活自定义传送门（当前：" + policy(policy).resolve(Lang.ZH) + "）");
    }

    // ---------------------------------------------------------------------------------------------
    // /pairedportals help
    // ---------------------------------------------------------------------------------------------

    /** The thirteen help lines, in the order they are printed. */
    public static final Text HELP_LIST = Text.of(
            "/pairedportals list [bound|unbound] — head count, then one line per door: full id, dimension,"
                    + " hole corner, size, state, sign words, and for a bound door where its twin is",
            "/pairedportals list [bound|unbound] —— 先给出总数，然后每扇门一行：完整 id、维度、门洞角点、"
                    + "尺寸、状态、告示牌文字；已绑定的还会给出对门的位置");

    public static final Text HELP_INFO = Text.of(
            "/pairedportals info <id|pos> — one door in full, including its twin; with a position,"
                    + " the door that owns that block",
            "/pairedportals info <id|pos> —— 完整列出一扇门，包含它的对门；给坐标时，回答该方块属于哪扇门");

    public static final Text HELP_DELETE = Text.of(
            "/pairedportals delete <id|pos> — remove that door: both holes go dark, both records go away",
            "/pairedportals delete <id|pos> —— 删除这扇门：两个门洞都熄灭，两条记录都移除");

    public static final Text HELP_PURGE = Text.of(
            "/pairedportals purge — sweep now and retire every portal whose frame no longer stands",
            "/pairedportals purge —— 立即巡检，回收所有框架已不成立的传送门");

    public static final Text HELP_TP = Text.of(
            "/pairedportals tp <id|pos> — teleport to that door (cross-dimension and cross-world),"
                    + " landing in front of it",
            "/pairedportals tp <id|pos> —— 传送到那扇门（可跨维度、跨世界），落点在门前");

    public static final Text HELP_UNBIND = Text.of(
            "/pairedportals unbind <id|pos> — break the pairing only: both doors stay lit, both wait again",
            "/pairedportals unbind <id|pos> —— 只解除配对：两扇门都保持点亮，都重新进入等待");

    public static final Text HELP_PAIR = Text.of(
            "/pairedportals pair <idA> <idB> — bind two waiting doors by hand (either dimension,"
                    + " or both in one)",
            "/pairedportals pair <idA> <idB> —— 手动绑定两扇等待中的门（可以跨维度，也可以在同一维度）");

    public static final Text HELP_PERMISSION = Text.of(
            "/pairedportals permission [all|op|none] — who may light a NEW door "
                    + "(all = anyone, op = operators only, none = nobody); doors that already exist keep working",
            "/pairedportals permission [all|op|none] —— 谁能点亮“新的”门"
                    + "（all = 所有人，op = 仅 OP，none = 所有人都不行）；已经存在的门照常工作");

    public static final Text HELP_DEBUG = Text.of(
            "/pairedportals debug <pos> — replay the ignition decision at a position without building anything",
            "/pairedportals debug <pos> —— 在不建造任何东西的前提下，重放该位置的点燃判定");

    public static final Text HELP_RULE = Text.of(
            "A door is unbound until a second door carrying the same sign words is lit; the two pair up"
                    + " in ignition order (first come, first served) and from then on travel is between them only.",
            "一扇门在被点亮时处于未绑定状态，直到另一扇写着同样文字的门被点亮；两扇门按点燃的先后顺序配对"
                    + "（先来先得），此后只在这两扇门之间通行。");

    public static final Text HELP_CROSS_WORLD = Text.of(
            "Cross-world: the two ends may stand in different worlds — a door in one world leads to its"
                    + " twin in another, and a registered world that is not loaded is loaded on demand by the trip"
                    + " itself. If the far world cannot be reached the door stays lit and keeps its pairing, and it"
                    + " waits rather than being taken down; 'list' and 'info' say which of the two it is.",
            "跨世界：两端可以位于不同世界 —— 一个世界里的门通往另一个世界里的对门，而已注册但尚未加载的世界"
                    + "会由这次通行本身按需加载。如果对面的世界无法抵达，门会保持点亮并保留配对，它会等待而不是被拆除；"
                    + "'list' 和 'info' 会说明是哪一种情况。");

    // ---------------------------------------------------------------------------------------------
    // /pairedportals list|info
    // ---------------------------------------------------------------------------------------------

    /**
     * The head count: how many doors, how many complete pairs, how many still waiting.
     *
     * <p>Its {@code N portal(s):} prefix is the shape {@code test/smoke-*.sh} reads the registry
     * through, which is why the English column keeps it verbatim.
     */
    public static final Text LIST_HEAD = Text.of(
            "%d portal(s): %d bound pair(s) (%d door(s)), %d door(s) waiting for a partner; activation: %s (%s)",
            "%d 个传送门：%d 对已绑定（共 %d 扇门），%d 扇门在等待配对；激活权限：%s（%s）");

    public static final Text LIST_EMPTY = Text.of(
            "no custom portals are registered",
            "没有注册任何自定义传送门");

    public static final Text INFO_ID = Text.of("id: %s", "id：%s");

    public static final Text INFO_DIMENSION = Text.of("dimension: %s", "维度：%s");

    public static final Text INFO_HOLE = Text.of(
            "hole: %s .. %s (%sx%s, axis %s)",
            "门洞：%s .. %s（%s×%s，轴向 %s）");

    public static final Text INFO_STATE = Text.of("state: %s", "状态：%s");

    public static final Text INFO_BINDING_BOUND = Text.of(
            "binding text: '%s' (paired; a second frame with these words pairs here)",
            "配对文字：'%s'（已配对；再有写着这句话的门框会连到这里）");

    public static final Text INFO_BINDING_PENDING = Text.of(
            "binding text: '%s' (waiting for the second frame with these words)",
            "配对文字：'%s'（等待第二个写着这句话的门框）");

    public static final Text INFO_ORDER = Text.of("ignition order: %s", "点燃序号：%s");

    public static final Text INFO_CREATED = Text.of("created: %s", "创建时间：%s");

    public static final Text INFO_TWIN = Text.of("twin: %s", "对门：%s");

    public static final Text INFO_TWIN_WORLD = Text.of(
            "twin world: %s — %s",
            "对门所在世界：%s —— %s");

    public static final Text TWIN_NONE = Text.of("none", "无");

    public static final Text TWIN_MISSING = Text.of(
            "%s (missing from the registry)",
            "%s（注册表中已不存在）");

    public static final Text TWIN_AT = Text.of(
            "%s at %s in %s [%s]",
            "%s，位于 %s 的 %s [%s]");

    // ---------------------------------------------------------------------------------------------
    // Failures shared by the subcommands that take <id|pos>
    // ---------------------------------------------------------------------------------------------

    public static final Text FAIL_NO_ID = Text.of(
            "no portal with id %s",
            "没有 id 为 %s 的传送门");

    public static final Text FAIL_NO_OWNER = Text.of(
            "no portal owns %s (neither a lit block of a hole nor a frame block around one)",
            "没有传送门占据 %s（既不是门洞里的传送门方块，也不是它周围的门框方块）");

    /** {@code delete} by position answers shorter than the rest: the long form is for the other three. */
    public static final Text FAIL_NO_OWNER_SHORT = Text.of(
            "no portal owns %s",
            "没有传送门占据 %s");

    // ---------------------------------------------------------------------------------------------
    // /pairedportals tp
    // ---------------------------------------------------------------------------------------------

    public static final Text TP_NO_WORLD = Text.of(
            "no such world: %s (not loaded, and nothing on this server can load it)",
            "没有这个世界：%s（未加载，而且本服务器上没有任何东西能把它加载起来）");

    public static final Text TP_DONE = Text.of(
            "teleported to portal %s at %s in %s",
            "已传送到传送门 %s，位于 %s 的 %s");

    /** Appended to {@link #TP_DONE} when the lookup itself brought the world up. */
    public static final Text TP_LOADED_TAIL = Text.of(
            " (this loaded that world)",
            "（这一步把那个世界加载了起来）");

    // ---------------------------------------------------------------------------------------------
    // /pairedportals unbind
    // ---------------------------------------------------------------------------------------------

    public static final Text UNBIND_ALREADY = Text.of(
            "portal %s is already waiting for a partner; nothing to unbind",
            "传送门 %s 已经在等待配对，无需解除绑定");

    public static final Text UNBIND_DONE_LONE = Text.of(
            "unbound portal %s at %s (its twin was already gone)",
            "已解除传送门 %s（位于 %s）的绑定（它的对门此前已不存在）");

    public static final Text UNBIND_DONE_TWIN = Text.of(
            "unbound portal %s at %s and its twin at %s",
            "已解除传送门 %s（位于 %s）与其对门（位于 %s）的绑定");

    public static final Text UNBIND_NOTE = Text.of(
            "both doors stay lit; the next matching ignition can take either of them,"
                    + " or 'pair' can join these two back up",
            "两扇门都保持点亮；下一次文字相符的点燃可以选中其中任意一扇，也可以用 'pair' 把它们重新配起来");

    // ---------------------------------------------------------------------------------------------
    // /pairedportals pair
    // ---------------------------------------------------------------------------------------------

    public static final Text PAIR_SELF = Text.of(
            "a door cannot be its own twin",
            "一扇门不能与自身配对");

    public static final Text PAIR_ALREADY_BOUND = Text.of(
            "both doors have to be waiting; %s is already bound — 'unbind' it first",
            "两扇门都必须处于等待状态；%s 已经绑定 —— 请先用 'unbind' 解除绑定");

    public static final Text PAIR_SAME_DIMENSION = Text.of(
            "both doors are in %s — a same-dimension pair works like any other",
            "两扇门都在 %s —— 同维度配对与其它配对完全一样");

    public static final Text PAIR_CLOSE_TOGETHER = Text.of(
            " (they are only %s blocks apart; the return leg still waits out the portal cooldown)",
            "（两者仅相距 %s 格；折返时仍需等完传送门的冷却）");

    public static final Text PAIR_DONE = Text.of(
            "paired %s (%s %s) <-> %s (%s %s)",
            "已配对 %s（%s %s）<-> %s（%s %s）");

    public static final Text PAIR_TEXT_DIFFERS = Text.of(
            "note: their sign words differ ('%s' vs '%s') — the pair works, but nothing in the world"
                    + " explains why these two are one door",
            "注意：两块告示牌的文字不同（'%s' 与 '%s'）—— 配对照常工作，"
                    + "但世界上没有任何东西能说明这两扇门为什么算一对");

    // ---------------------------------------------------------------------------------------------
    // /pairedportals delete
    // ---------------------------------------------------------------------------------------------

    public static final Text DELETE_LONE = Text.of(
            "deleted portal %s (it had no twin)",
            "已删除传送门 %s（它没有对门）");

    public static final Text DELETE_WITH_TWIN = Text.of(
            "deleted portal %s and took its twin at %s down with it",
            "已删除传送门 %s，并同时拆除了它在 %s 的对门");

    // ---------------------------------------------------------------------------------------------
    // /pairedportals permission
    // ---------------------------------------------------------------------------------------------

    public static final Text PERMISSION_QUERY = Text.of(
            "activation: %s (%s)",
            "激活权限：%s（%s）");

    public static final Text PERMISSION_NONE = Text.of(
            "nobody may light a new door; existing doors still work",
            "所有人都不能点亮新的门；已经存在的门照常工作");

    public static final Text PERMISSION_WHO = Text.of(
            "who may light a new door: %s",
            "谁可以点亮新的门：%s");

    public static final Text PERMISSION_SET = Text.of(
            "activation set to %s (%s)",
            "激活权限已设为 %s（%s）");

    // ---------------------------------------------------------------------------------------------
    // /pairedportals purge
    // ---------------------------------------------------------------------------------------------

    public static final Text PURGE_CLEAN = Text.of(
            "sweep finished: every loaded portal still matches the world "
                    + "(portals in unloaded chunks were not judged)",
            "巡检结束：所有已加载的传送门都与世界相符（未加载区块中的传送门没有被判定）");

    public static final Text PURGE_RETIRED = Text.of(
            "sweep retired %s portal(s) whose frame no longer stands",
            "巡检回收了 %s 个框架已不成立的传送门");

    // ---------------------------------------------------------------------------------------------
    // Helpers for the two subcommands that print a record's twin
    // ---------------------------------------------------------------------------------------------

    /** The twin field of {@code info}: no twin, a claimed twin that is gone, or a live twin. */
    public static Text twinField(UUID twinId, String pos, String dimension, String state) {
        if (twinId == null) {
            return TWIN_NONE;
        }
        return pos == null
                ? TWIN_MISSING.formatted(twinId)
                : TWIN_AT.formatted(twinId, pos, dimension, state);
    }
}
