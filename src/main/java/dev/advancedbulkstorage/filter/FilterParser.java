package dev.advancedbulkstorage.filter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;

final class FilterParser {
    private final String expression;
    private final FilterCapabilities capabilities;
    private final Set<ResourceLocation> tags = new HashSet<>();
    private final Set<String> namespaces = new HashSet<>();
    private int position;
    private int conditions;
    private int expansions;

    private FilterParser(String expression, FilterCapabilities capabilities) {
        this.expression = expression;
        this.capabilities = capabilities;
    }

    static IntPredicate parse(FilterConfig config, FilterCapabilities capabilities) {
        var parser = new FilterParser(config.expression(), capabilities);
        parser.require(!config.blacklist(), "legacy_blacklist");
        parser.require(parser.expression.length() <= capabilities.length(), "too_long");
        parser.whitespace();
        if (parser.position == parser.expression.length()) {
            return id -> false;
        }
        var rule = parser.parseJunction(0, false);
        parser.whitespace();
        parser.require(parser.position == parser.expression.length(), "syntax");
        parser.require(rule.scoped(false), "unbounded");

        var candidates = new BitSet();
        rule.candidates(candidates, false);
        candidates.clear(BuiltInRegistries.ITEM.getId(Items.AIR));
        var accepted = new BitSet();
        int matched = 0;
        for (int id = candidates.nextSetBit(0); id >= 0; id = candidates.nextSetBit(id + 1)) {
            var result = rule.evaluate(id, false);
            if (result != Match.NO) {
                matched++;
                if (!FilterEngine.isEquipment(id) || result == Match.EXACT && capabilities.exactEquipment()) {
                    accepted.set(id);
                }
            }
        }
        parser.require(matched < BuiltInRegistries.ITEM.size() - 1, "unbounded");
        return accepted::get;
    }

    private Node parseJunction(int depth, boolean and) {
        var first = and ? unary(depth) : parseJunction(depth, true);
        char operator = and ? '&' : '|';
        if (!consume(operator)) {
            return first;
        }
        require(and ? capabilities.and() : capabilities.or(), "unsupported");
        List<Node> terms = new ArrayList<>();
        terms.add(first);
        do {
            terms.add(and ? unary(depth) : parseJunction(depth, true));
        } while (consume(operator));
        return new Junction(List.copyOf(terms), and);
    }

    private Node unary(int depth) {
        require(depth <= capabilities.depth(), "depth");
        if (consume('!')) {
            require(capabilities.not(), "unsupported");
            return new Negation(unary(depth + 1));
        }
        if (consume('(')) {
            require(capabilities.groups(), "unsupported");
            var group = parseJunction(depth + 1, false);
            require(consume(')'), "syntax");
            return group;
        }
        return atom();
    }

    private Node atom() {
        require(++conditions <= capabilities.conditions(), "conditions");
        int start = position;
        boolean tag = consume('#');
        boolean namespace = !tag && consume('@');
        int idStart = position;
        while (position < expression.length() && identifierCharacter(expression.charAt(position))) {
            position++;
        }
        require(position > idStart, "syntax");
        require(position - idStart <= capabilities.identifierLength(), "identifier_length");
        String selector = expression.substring(idStart, position);
        if (selector.equals("*") || selector.equals("*:*")) {
            throw new Failure("unbounded", start);
        }
        if (namespace) {
            require(capabilities.namespaces(), "unsupported");
            return namespace(selector, start);
        }
        require(tag ? capabilities.tags() : capabilities.items(), "unsupported");
        int separator = selector.indexOf(':');
        require(separator > 0 && separator < selector.length() - 1 && selector.lastIndexOf(':') == separator, "syntax");
        String domain = selector.substring(0, separator);
        String path = selector.substring(separator + 1);
        require(ResourceLocation.isValidNamespace(domain)
                && ResourceLocation.tryBuild(domain, path.replace('*', 'a')) != null, "syntax");
        boolean wildcard = path.indexOf('*') >= 0;
        if (tag) {
            return tag(domain, path, wildcard, start);
        }
        return item(domain, path, wildcard, start);
    }

    private Node namespace(String selector, int start) {
        boolean wildcard = selector.indexOf('*') >= 0;
        require(ResourceLocation.isValidNamespace(selector.replace('*', 'a')), "syntax");
        var selected = new BitSet();
        boolean found = false;
        for (var entry : FilterEngine.namespaces().entrySet()) {
            if (glob(selector, entry.getKey())) {
                found = true;
                if (wildcard) {
                    consumeExpansion(start);
                }
                namespaces.add(entry.getKey());
                require(namespaces.size() <= capabilities.namespaceLimit(), "namespace_limit");
                selected.or(entry.getValue());
            }
        }
        require(found, "unknown_namespace");
        return new Atom(selected, false);
    }

    private Node tag(String domain, String path, boolean wildcard, int start) {
        var selected = new BitSet();
        if (wildcard) {
            boolean found = false;
            for (var entry : FilterEngine.tags().entrySet()) {
                var id = entry.getKey();
                if (id.getNamespace().equals(domain) && glob(path, id.getPath())) {
                    found = true;
                    consumeExpansion(start);
                    addTag(selected, id, entry.getValue(), start);
                }
            }
            require(found, "unknown_tag");
        } else {
            var id = ResourceLocation.fromNamespaceAndPath(domain, path);
            var members = FilterEngine.tags().get(id);
            require(members != null, "unknown_tag");
            addTag(selected, id, members, start);
        }
        return new Atom(selected, false);
    }

    private void addTag(BitSet selected, ResourceLocation id, BitSet members, int start) {
        if (FilterEngine.isEquipmentTag(id, members)) {
            throw new Failure("equipment_broad", start);
        }
        tags.add(id);
        require(tags.size() <= capabilities.tagLimit(), "tag_limit");
        selected.or(members);
    }

    private Node item(String domain, String path, boolean wildcard, int start) {
        var selected = new BitSet();
        if (!wildcard) {
            var holder = BuiltInRegistries.ITEM.getHolder(ResourceLocation.fromNamespaceAndPath(domain, path));
            require(holder.isPresent(), "unknown_item");
            int id = BuiltInRegistries.ITEM.getId(holder.orElseThrow().value());
            require(capabilities.exactEquipment() || !FilterEngine.isEquipment(id), "equipment_exact_disabled");
            selected.set(id);
            return new Atom(selected, true);
        }
        var domainItems = FilterEngine.namespaces().get(domain);
        require(domainItems != null, "unknown_namespace");
        for (int id = domainItems.nextSetBit(0); id >= 0; id = domainItems.nextSetBit(id + 1)) {
            var registered = BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.byId(id));
            if (glob(path, registered.getPath())) {
                consumeExpansion(start);
                selected.set(id);
            }
        }
        require(!selected.isEmpty(), "unknown_item");
        require(!FilterEngine.allEquipment(selected), "equipment_broad");
        return new Atom(selected, false);
    }

    private void consumeExpansion(int start) {
        if (++expansions > capabilities.globMatches()) {
            throw new Failure("glob_limit", start);
        }
    }

    // The last star is the only backtracking point. Inputs and candidate registry identifiers are bounded.
    private static boolean glob(String pattern, String value) {
        int p = 0, v = 0, star = -1, retry = 0;
        while (v < value.length()) {
            if (p < pattern.length() && pattern.charAt(p) == value.charAt(v)) {
                p++;
                v++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                retry = v;
            } else if (star >= 0) {
                p = star + 1;
                v = ++retry;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }

    private static boolean identifierCharacter(char c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
                || c == '_' || c == '-' || c == '.' || c == '/' || c == ':' || c == '*';
    }

    private boolean consume(char symbol) {
        whitespace();
        if (position < expression.length() && expression.charAt(position) == symbol) {
            position++;
            return true;
        }
        return false;
    }

    private void whitespace() {
        while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) {
            position++;
        }
    }

    private void require(boolean valid, String reason) {
        if (!valid) {
            throw new Failure(reason, position);
        }
    }

    private enum Match { NO, YES, EXACT }

    private interface Node {
        Match evaluate(int id, boolean negated);
        boolean scoped(boolean negated);
        void candidates(BitSet result, boolean negated);
    }

    private record Atom(BitSet items, boolean exact) implements Node {
        public Match evaluate(int id, boolean negated) {
            return items.get(id) == negated ? Match.NO : exact && !negated ? Match.EXACT : Match.YES;
        }

        public boolean scoped(boolean negated) {
            return !negated;
        }

        public void candidates(BitSet result, boolean negated) {
            if (!negated) {
                result.or(items);
            }
        }
    }

    private record Negation(Node inner) implements Node {
        public Match evaluate(int id, boolean negated) { return inner.evaluate(id, !negated); }
        public boolean scoped(boolean negated) { return inner.scoped(!negated); }
        public void candidates(BitSet result, boolean negated) { inner.candidates(result, !negated); }
    }

    private record Junction(List<Node> terms, boolean and) implements Node {
        public Match evaluate(int id, boolean negated) {
            boolean conjunction = and != negated;
            Match result = conjunction ? Match.YES : Match.NO;
            for (var term : terms) {
                var match = term.evaluate(id, negated);
                if (conjunction && match == Match.NO) {
                    return Match.NO;
                }
                if (match == Match.EXACT || result == Match.NO && match == Match.YES) {
                    result = match;
                }
            }
            return result;
        }

        public boolean scoped(boolean negated) {
            // In negation-normal form, each successful OR branch needs an anchor.
            boolean conjunction = and != negated;
            for (var term : terms) {
                if (term.scoped(negated) == conjunction) {
                    return conjunction;
                }
            }
            return !conjunction;
        }

        public void candidates(BitSet result, boolean negated) {
            for (var term : terms) {
                term.candidates(result, negated);
            }
        }
    }

    static final class Failure extends RuntimeException {
        private final int position;

        Failure(String reason, int position) {
            super(reason, null, false, false);
            this.position = position;
        }

        int position() { return position; }
    }
}
