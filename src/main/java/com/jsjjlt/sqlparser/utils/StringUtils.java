package com.jsjjlt.sqlparser.utils;

public final class StringUtils {
    public static boolean isWrappedInCharacter(String input, Character ch) {
        return input.length() >= 2 && input.charAt(0) == ch && input.charAt(input.length() - 1) == ch;
    }

    public static String handleQuoter(String input) {
        if (input == null) return null;
        if (StringUtils.isWrappedInCharacter(input, '`')) {
            input = input.substring(1, input.length() - 1);
        } else if (StringUtils.isWrappedInCharacter(input, '"')) {
            input = input.substring(1, input.length() - 1);
        } else if (StringUtils.isWrappedInCharacter(input, '\'')) {
            input = input.substring(1, input.length() - 1);
        }
        return input;
    }
}