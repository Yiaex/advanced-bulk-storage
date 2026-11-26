package dev.advancedbulkstorage.storage;

import appeng.api.stacks.AEItemKey;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.filter.FilterCapabilities;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.filter.FilterEngine;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Limits are written as <amount> <selector>, separated by semicolons. */
public record LimitProfile(long each, long total, boolean voidEach, boolean voidTotal, List<Rule> rules, boolean valid) {
    public static final int MAX_LENGTH = 2048;
    private static final LimitProfile INVALID = new LimitProfile(0, 0, false, false, List.of(), false);

    public record Rule(long maximum, FilterEngine.Compiled filter, boolean discard) {}

    public boolean hasOverflow() {
        return voidEach || voidTotal || rules.stream().anyMatch(Rule::discard);
    }

    public static LimitProfile parse(String text) {
        if (text.length() > MAX_LENGTH) {
            return INVALID;
        }
        long each = -1;
        long total = -1;
        boolean voidEach = false;
        boolean voidTotal = false;
        var rules = new ArrayList<Rule>();
        for (var part : text.split(";", -1)) {
            String line = part.strip();
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf(' ');
            if (separator < 1) {
                return INVALID;
            }
            long amount;
            try {
                amount = Long.parseLong(line.substring(0, separator));
            } catch (NumberFormatException invalid) {
                return INVALID;
            }
            if (amount < 0) {
                return INVALID;
            }
            var selector = line.substring(separator + 1).strip();
            boolean discard = selector.endsWith(" !void");
            if (discard) {
                selector = selector.substring(0, selector.length() - " !void".length()).strip();
            }
            if (selector.equals("each") && each == -1) {
                each = amount;
                voidEach = discard;
            } else if (selector.equals("total") && total == -1) {
                total = amount;
                voidTotal = discard;
            } else {
                var filter = FilterEngine.compile(new FilterConfig(selector, false),
                        FilterCapabilities.forCell(CellTier.QUANTUM, false));
                if (filter.error() != null || rules.size() >= 24) {
                    return INVALID;
                }
                rules.add(new Rule(amount, filter, discard));
            }
        }
        return new LimitProfile(each, total, voidEach, voidTotal, List.copyOf(rules), true);
    }

    static final class Ledger {
        final LimitProfile profile;
        private final BigInteger[] groups;
        private BigInteger total = BigInteger.ZERO;
        private final java.util.Map<AEItemKey, BigInteger> eachCounts;
        private int fullKeys;

        Ledger(LimitProfile profile) {
            this.profile = profile;
            groups = new BigInteger[profile.rules.size()];
            eachCounts = profile.each >= 0 ? new java.util.HashMap<>() : null;
            java.util.Arrays.fill(groups, BigInteger.ZERO);
        }

        void change(AEItemKey key, long delta) {
            if (!profile.valid || delta == 0) {
                return;
            }
            var value = BigInteger.valueOf(delta);
            if (eachCounts != null && profile.each > 0) {
                var previous = eachCounts.getOrDefault(key, BigInteger.ZERO);
                var current = previous.add(value);
                if (current.signum() == 0) eachCounts.remove(key);
                else eachCounts.put(key, current);
                var cap = BigInteger.valueOf(profile.each);
                if (previous.compareTo(cap) >= 0) fullKeys--;
                if (current.compareTo(cap) >= 0) fullKeys++;
            }
            if (profile.total >= 0) {
                total = total.add(value);
            }
            for (int index = 0; index < groups.length; index++) {
                if (profile.rules.get(index).filter().matches(key)) {
                    groups[index] = groups[index].add(value);
                }
            }
        }

        long remaining(AEItemKey key, long stored) {
            long available = profile.valid ? Long.MAX_VALUE : 0;
            if (profile.each >= 0) available = Math.min(available, Math.max(0, profile.each - stored));
            if (profile.total >= 0) available = Math.min(available, remaining(profile.total, total));
            for (int i = 0; i < groups.length; i++) {
                var rule = profile.rules.get(i);
                if (rule.filter().matches(key)) available = Math.min(available, remaining(rule.maximum(), groups[i]));
            }
            return available;
        }

        boolean atLimit() {
            if (profile.each == 0 || fullKeys > 0 || profile.total >= 0 && remaining(profile.total, total) == 0) return true;
            for (int i = 0; i < groups.length; i++) {
                if (remaining(profile.rules.get(i).maximum(), groups[i]) == 0) return true;
            }
            return false;
        }

        long remaining(AEItemKey key, long stored, boolean discardRules) {
            if (!profile.valid) {
                return 0;
            }
            long result = profile.each < 0 || profile.voidEach != discardRules
                    ? Long.MAX_VALUE : Math.max(0, profile.each - stored);
            if (profile.total >= 0 && profile.voidTotal == discardRules) {
                result = Math.min(result, remaining(profile.total, total));
            }
            for (int index = 0; index < groups.length; index++) {
                var rule = profile.rules.get(index);
                if (rule.discard() == discardRules && rule.filter().matches(key)) {
                    result = Math.min(result, remaining(rule.maximum(), groups[index]));
                }
            }
            return result;
        }

        private static long remaining(long cap, BigInteger count) {
            if (count.signum() < 0 || count.bitLength() > 63) {
                return 0;
            }
            return Math.max(0, cap - count.longValue());
        }
    }
}
