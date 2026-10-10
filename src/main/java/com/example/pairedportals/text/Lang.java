package com.example.pairedportals.text;

import java.util.Locale;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * The language one message is written in.
 *
 * <p>Why this exists at all: the mod is server-side only ({@code "environment": "server"} in
 * {@code fabric.mod.json}), so it has no client-side half and can never ship a language file. A
 * {@code Component.translatable(...)} would therefore be sent to the client as a key nobody can
 * resolve and arrive as raw {@code pairedportals.message.pending} text. The server has to pick the
 * wording itself, per player, and send a literal.
 *
 * <p>Where the answer comes from: {@link ServerPlayer#clientInformation()} carries the language code
 * the client reported when it joined ({@code language()}), which is the only per-player signal the
 * server has — and the right one, because a player's client language is what their own UI is in.
 *
 * <p>The split is deliberately crude: anything starting {@code zh} is Chinese, everything else is
 * English. Two languages are what this mod is written in, and inventing a third answer (a "partially
 * translated" or "unmatched key" state) would mean shipping strings nobody wrote.
 *
 * <p>Console senders — the server console, command blocks, {@code /execute as @e} — have no client
 * language at all, so they get English. That is also what keeps the smoke scripts honest: their
 * assertions read English output, and console output stays English no matter who else is online.
 */
public enum Lang {
    /** English: the default, and the only language server logs and debug output are written in. */
    EN,
    /** Chinese: used for a player whose client reports a {@code zh*} language. */
    ZH;

    /** The language {@code player}'s client is set to. */
    public static Lang of(ServerPlayer player) {
        return isChinese(player.clientInformation().language()) ? ZH : EN;
    }

    /**
     * The language of a command's sender, or English when the sender is not a player.
     *
     * <p>The console and command blocks are read by operators, and the same output is grep'd by the
     * smoke scripts, so they are pinned to English rather than following an arbitrary online player.
     */
    public static Lang of(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? EN : of(player);
    }

    /** Whether a client language code names Chinese, in any of its spellings. */
    public static boolean isChinese(String language) {
        String code = language.toLowerCase(Locale.ROOT);
        return code.equals("zh") || code.startsWith("zh_") || code.startsWith("zh-");
    }
}
