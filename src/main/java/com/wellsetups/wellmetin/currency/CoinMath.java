package com.wellsetups.wellmetin.currency;

public final class CoinMath {
    public enum Operation { ADD, REMOVE, SET }

    private CoinMath() { }

    public static long amount(String text) {
        if (text == null || !text.matches("[0-9]{1,19}")) {
            throw new IllegalArgumentException("amount must be a nonnegative whole number");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("amount exceeds the maximum long value", failure);
        }
    }

    public static long apply(long balance, long amount, Operation operation) {
        if (balance < 0 || amount < 0) {
            throw new IllegalArgumentException("balance and amount must be nonnegative");
        }
        long result = switch (operation) {
            case ADD -> Math.addExact(balance, amount);
            case REMOVE -> Math.subtractExact(balance, amount);
            case SET -> amount;
        };
        if (result < 0) {
            throw new IllegalArgumentException("insufficient balance");
        }
        return result;
    }
}
