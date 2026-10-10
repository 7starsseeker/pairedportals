package com.example.pairedportals.text;

/**
 * One player-visible message, written out in every language this mod speaks.
 *
 * <p>Both columns are carried in one object on purpose. The alternative — a key plus two maps — buys
 * nothing here: there is no build step that could catch a missing translation (the mod has no Gradle
 * and no resource pipeline), so the only thing that can is a reader seeing the two lines next to each
 * other in {@link Messages}, and a compiler noticing that a factory has to produce both.
 *
 * <p>Resolution happens once, at the edge, where the recipient is known ({@code Lang.of(...)}); nothing
 * below that knows a language exists.
 */
public record Text(String en, String zh) {

    /** A message whose two languages are given explicitly. */
    public static Text of(String en, String zh) {
        return new Text(en, zh);
    }

    /** The message in {@code lang}. */
    public String resolve(Lang lang) {
        return lang == Lang.ZH ? zh : en;
    }

    /** Both columns with the same arguments filled in, for a message that carries values. */
    public Text formatted(Object... args) {
        return new Text(en.formatted(args), zh.formatted(args));
    }

    /**
     * The message in {@code lang}, with {@link String#format} applied.
     *
     * <p>A {@code Text} argument is resolved with the same {@code lang} first, so a message can be
     * assembled out of other messages — {@code LIST_HEAD} carries the activation policy label, and the
     * label follows the reader rather than the caller.
     */
    public String format(Lang lang, Object... args) {
        Object[] resolved = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            resolved[i] = args[i] instanceof Text text ? text.resolve(lang) : args[i];
        }
        return String.format(resolve(lang), resolved);
    }
}
