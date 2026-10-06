package com.wellsetups.wellmetin.lang;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorParser {
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})|<#([0-9a-fA-F]{6})>");
    private static final Pattern LEGACY = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private ColorParser() { }

    public static String parse(String text) {
        Matcher matcher = HEX.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            StringBuilder color = new StringBuilder("\u00a7x");
            for (char character : hex.toCharArray()) {
                color.append('\u00a7').append(Character.toLowerCase(character));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(color.toString()));
        }
        matcher.appendTail(result);
        return LEGACY.matcher(result).replaceAll(match -> "\u00a7" + match.group(1).toLowerCase(java.util.Locale.ROOT));
    }
}
