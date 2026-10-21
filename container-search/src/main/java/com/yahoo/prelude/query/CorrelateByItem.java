// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.api.annotations.Beta;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * Requires matching rows in every clause to agree on their shared variables.
 * A variable omitted by a clause is unbound there: a unit-only clause can match
 * the same unit as clauses which also bind a zone.
 *
 * <p>Each child is a sameElement clause. This item keeps the variables each clause binds,
 * the exact values its rows must have, and its firstPresent link, in the same order as the children.
 * Add children with {@link #addClause} and {@link #addFirstPresent}; the other ways of adding a child are refused.
 * Removing an ordinary child removes its clause. Neither clause of a firstPresent pair can be removed separately.</p>
 *
 * <p>An exact value, written <code>field = "value"</code> in YQL, requires the whole value of the
 * row's field to equal the given string. Case is handled as the field handles the case of its terms:
 * {@link #lowercaseValues} lowercases exact values and variable values for fields which ignore case.</p>
 */
@Beta
public class CorrelateByItem extends NonReducibleCompositeItem {

    /** The variable and its values in priority order for a clause matched at the first value which is present. */
    public record FirstPresent(String variable, List<String> values) {

        public FirstPresent {
            Objects.requireNonNull(variable, "variable");
            if (variable.isBlank()) throw new IllegalArgumentException("The firstPresent variable must be nonempty");
            values = List.copyOf(values);
        }

    }

    /** A sameElement clause, the variables it binds to its struct fields, and the exact values its rows must have. */
    public record Clause(SameElementItem item, Map<String, String> bindings, Map<String, List<String>> exactValues) {

        public Clause {
            Objects.requireNonNull(item, "item");
            if (bindings.isEmpty()) throw new IllegalArgumentException("A correlated clause must bind at least one variable");
            for (var binding : bindings.entrySet()) {
                if (binding.getKey().isBlank() || binding.getValue().isBlank())
                    throw new IllegalArgumentException("Binding variables and fields must be nonempty");
            }
            bindings = sorted(bindings);
            var copied = new TreeMap<String, List<String>>();
            exactValues.forEach((field, values) -> {
                if (field.isBlank()) throw new IllegalArgumentException("Exact value fields must be nonempty");
                copied.put(field, List.copyOf(values));
            });
            exactValues = Collections.unmodifiableMap(copied);
        }

        public Clause(SameElementItem item, Map<String, String> bindings) {
            this(item, bindings, Map.of());
        }

    }

    /** What this keeps about each child. A presence clause always follows the clause holding its firstPresent. */
    private record ClauseInfo(Map<String, String> bindings, Map<String, List<String>> exactValues,
                              FirstPresent firstPresent, boolean presence) { }

    private List<ClauseInfo> clauses = new ArrayList<>();
    private Map<String, List<String>> allowedValues = Map.of();

    /** Adds a clause. */
    public void addClause(Clause clause) {
        super.addItem(clause.item());
        clauses.add(new ClauseInfo(clause.bindings(), clause.exactValues(), null, false));
    }

    /**
     * Adds a clause which is evaluated at the first of the given values of the variable for which
     * the presence clause has a matching row. Both clauses must bind the same variables to the same fields,
     * and the presence clause should test that a key is present independently of the value the first clause tests.
     */
    public void addFirstPresent(Clause clause, Clause presence, String variable, List<String> values) {
        var firstPresent = new FirstPresent(variable, values);
        if ( ! clause.bindings().containsKey(variable))
            throw new IllegalArgumentException("The firstPresent variable must be bound by the clause");
        if ( ! clause.bindings().equals(presence.bindings()))
            throw new IllegalArgumentException("firstPresent clauses must bind the same variables to the same fields");
        if (clause.item() == presence.item() || ! clause.item().getFieldName().equals(presence.item().getFieldName()))
            throw new IllegalArgumentException("firstPresent requires two different clauses on the same struct field");
        // Check both clauses before changing the children or their metadata.
        ensureNotInSubtree(clause.item());
        ensureNotInSubtree(presence.item());
        super.addItem(clause.item());
        super.addItem(presence.item());
        clauses.add(new ClauseInfo(clause.bindings(), clause.exactValues(), firstPresent, false));
        clauses.add(new ClauseInfo(presence.bindings(), presence.exactValues(), null, true));
    }

    /** Returns the variables the clause at the given position binds, each mapped to its struct field. */
    public Map<String, String> getBindings(int clause) { return clauses.get(clause).bindings(); }

    /** Returns the exact values required by the clause at the given position, by struct field. */
    public Map<String, List<String>> getExactValues(int clause) { return clauses.get(clause).exactValues(); }

    /** Returns the firstPresent rule of the clause at the given position. Its presence clause is the next clause. */
    public Optional<FirstPresent> getFirstPresent(int clause) {
        return Optional.ofNullable(clauses.get(clause).firstPresent());
    }

    /** Returns whether the clause at the given position is the presence clause of the clause before it. */
    public boolean isPresenceClause(int clause) { return clauses.get(clause).presence(); }

    /** Restricts a string variable to these values. An empty list matches nothing. */
    public void setAllowedValues(String variable, List<String> values) {
        if (variable.isBlank()) throw new IllegalArgumentException("Binding variable must be nonempty");
        var updated = new TreeMap<>(allowedValues);
        updated.put(variable, List.copyOf(values));
        allowedValues = Collections.unmodifiableMap(updated);
    }

    public Map<String, List<String>> getAllowedValues() { return allowedValues; }

    /**
     * Lowercases the exact values on fields which lowercase their terms, and the allowed and firstPresent values
     * of variables bound to such fields, so that these values compare the same way as the terms of the clauses.
     *
     * @param lowercases tells whether a field, given by its full name such as "signals.zone", lowercases its terms
     * @throws IllegalArgumentException if a variable with values is bound both to fields which lowercase and fields which do not
     */
    public void lowercaseValues(Predicate<String> lowercases) {
        for (int i = 0; i < clauses.size(); ++i) {
            var info = clauses.get(i);
            String field = clauseField(i);
            var exactValues = new TreeMap<String, List<String>>();
            info.exactValues().forEach((subfield, values) ->
                    exactValues.put(subfield, lowercases.test(field + "." + subfield) ? lowercase(values) : values));
            var firstPresent = info.firstPresent();
            if (firstPresent != null && lowercasesVariable(firstPresent.variable(), lowercases))
                firstPresent = new FirstPresent(firstPresent.variable(), lowercase(firstPresent.values()));
            clauses.set(i, new ClauseInfo(info.bindings(), Collections.unmodifiableMap(exactValues),
                                          firstPresent, info.presence()));
        }
        var updated = new TreeMap<String, List<String>>();
        allowedValues.forEach((variable, values) ->
                updated.put(variable, lowercasesVariable(variable, lowercases) ? lowercase(values) : values));
        allowedValues = Collections.unmodifiableMap(updated);
    }

    /** Returns the full names of the fields the given variable is bound to, such as "signals.zone". */
    private Set<String> boundFields(String variable) {
        Set<String> fields = new HashSet<>();
        for (int i = 0; i < clauses.size(); ++i) {
            String subfield = clauses.get(i).bindings().get(variable);
            if (subfield != null) fields.add(clauseField(i) + "." + subfield);
        }
        return fields;
    }

    private boolean lowercasesVariable(String variable, Predicate<String> lowercases) {
        var fields = boundFields(variable);
        boolean anyLowercases = fields.stream().anyMatch(lowercases);
        if (anyLowercases && ! fields.stream().allMatch(lowercases))
            throw new IllegalArgumentException("Variable '$" + variable + "' has values, but is bound both to fields " +
                                               "which ignore case and fields which do not: " + new TreeSet<>(fields));
        return anyLowercases;
    }

    private static List<String> lowercase(List<String> values) {
        return values.stream().map(value -> toLowerCase(value)).toList();
    }

    private String clauseField(int clause) {
        return ((SameElementItem) getItem(clause)).getFieldName();
    }

    /** Refused: add clauses with {@link #addClause} or {@link #addFirstPresent}, which also add their variable bindings. */
    @Override
    public void addItem(Item item) {
        throw new IllegalArgumentException("Add correlateBy clauses with addClause or addFirstPresent");
    }

    /** Refused: add clauses with {@link #addClause} or {@link #addFirstPresent}, which also add their variable bindings. */
    @Override
    public void addItem(int index, Item item) {
        throw new IllegalArgumentException("Add correlateBy clauses with addClause or addFirstPresent");
    }

    /** Replaces the sameElement query of a clause, keeping its variable bindings. */
    @Override
    public Item setItem(int index, Item item) {
        requireReplacement(index, item);
        return super.setItem(index, item);
    }

    @Override
    public Item removeItem(int index) {
        requireRemovable(index);
        Item removed = super.removeItem(index);
        clauses.remove(index);
        return removed;
    }

    @Override
    public boolean removeItem(Item item) {
        int index = getItemIndex(item);
        if (index < 0) return false;
        removeItem(index);
        return true;
    }

    @Override
    public ListIterator<Item> getItemIterator() {
        return new ClauseIterator(super.getItemIterator());
    }

    private static void requireSameElement(Item item) {
        if ( ! (item instanceof SameElementItem))
            throw new IllegalArgumentException("A correlateBy clause must be a sameElement item, not " + item);
    }

    private void requireReplacement(int index, Item item) {
        requireSameElement(item);
        ensureNotInSubtree((SameElementItem) item);
        var info = clauses.get(index);
        int partner = info.presence() ? index - 1 : info.firstPresent() != null ? index + 1 : -1;
        if (partner >= 0 && ! ((SameElementItem) item).getFieldName().equals(clauseField(partner)))
            throw new IllegalArgumentException("firstPresent clauses must use the same struct field");
        if (partner >= 0 && item == getItem(partner))
            throw new IllegalArgumentException("firstPresent requires two different clauses");
    }

    private void requireRemovable(int index) {
        var info = clauses.get(index);
        if (info.firstPresent() != null || info.presence())
            throw new IllegalArgumentException("Cannot remove one clause of a firstPresent pair");
    }

    @Override
    public ItemType getItemType() { return ItemType.CORRELATE_BY; }

    @Override
    public String getName() { return "CORRELATE_BY"; }

    @Override
    protected void encodeThis(ByteBuffer buffer, SerializationContext context) {
        throw new IllegalStateException("correlateBy requires the protobuf query protocol");
    }

    @Override
    protected void appendHeadingString(StringBuilder buffer) {
        buffer.append(getName());
        if ( ! allowedValues.isEmpty()) buffer.append(allowedValues);
        buffer.append(' ');
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        for (int i = 0; i < getItemCount(); ++i) {
            var info = clauses.get(i);
            if (i > 0) buffer.append(' ');
            buffer.append(info.bindings());
            if ( ! info.exactValues().isEmpty()) buffer.append(info.exactValues());
            if (info.firstPresent() != null)
                buffer.append("firstPresent(").append(info.firstPresent().variable()).append(info.firstPresent().values()).append(')');
            if (info.presence()) buffer.append("presence");
            buffer.append(':').append(getItem(i));
        }
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        if (getItemCount() == 0)
            throw new IllegalArgumentException("correlateBy requires at least one matching clause");
        var builder = SearchProtocol.ItemCorrelateBy.newBuilder();
        if (getLabel() != null) builder.setLabel(getLabel());
        for (int i = 0; i < getItemCount(); ++i) {
            var info = clauses.get(i);
            var clause = SearchProtocol.CorrelationClause.newBuilder().setQuery(getItem(i).toProtobuf(context));
            info.bindings().forEach((variable, field) -> clause.addBindings(
                    SearchProtocol.CorrelationBinding.newBuilder().setVariable(variable).setField(field)));
            info.exactValues().forEach((field, values) -> clause.addExactValues(
                    SearchProtocol.CorrelationStringValues.newBuilder().setField(field).addAllValues(values)));
            builder.addClauses(clause);
            if (info.firstPresent() != null)
                builder.addFirstPresent(SearchProtocol.CorrelationFirstPresent.newBuilder()
                        .setClause(i).setPresence(i + 1)
                        .setVariable(info.firstPresent().variable()).addAllValues(info.firstPresent().values()));
        }
        allowedValues.forEach((variable, values) -> builder.addDomains(
                SearchProtocol.CorrelationDomain.newBuilder().setVariable(variable).addAllValues(values)));
        return SearchProtocol.QueryTreeItem.newBuilder().setItemCorrelateBy(builder).build();
    }

    @Override
    public CorrelateByItem clone() {
        var copy = (CorrelateByItem) super.clone();
        copy.clauses = new ArrayList<>(clauses);
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if ( ! super.equals(other)) return false;
        var that = (CorrelateByItem) other;
        return clauses.equals(that.clauses) && allowedValues.equals(that.allowedValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), clauses, allowedValues);
    }

    private static <V> Map<String, V> sorted(Map<String, V> map) {
        return Collections.unmodifiableMap(new TreeMap<>(map));
    }

    /** Iterates over the clauses, and keeps the clause information in step when a clause is removed or replaced. */
    private class ClauseIterator implements ListIterator<Item> {

        private final ListIterator<Item> items;
        private int current = -1;

        ClauseIterator(ListIterator<Item> items) { this.items = items; }

        @Override public boolean hasNext() { return items.hasNext(); }
        @Override public boolean hasPrevious() { return items.hasPrevious(); }
        @Override public int nextIndex() { return items.nextIndex(); }
        @Override public int previousIndex() { return items.previousIndex(); }

        @Override
        public Item next() {
            int index = items.nextIndex();
            Item item = items.next();
            current = index;
            return item;
        }

        @Override
        public Item previous() {
            int index = items.previousIndex();
            Item item = items.previous();
            current = index;
            return item;
        }

        @Override
        public void remove() {
            if (current < 0) throw new IllegalStateException();
            requireRemovable(current);
            items.remove();
            clauses.remove(current);
            current = -1;
        }

        @Override
        public void set(Item item) {
            if (current < 0) throw new IllegalStateException();
            requireReplacement(current, item);
            items.set(item);
        }

        @Override
        public void add(Item item) {
            throw new IllegalArgumentException("Add correlateBy clauses with addClause or addFirstPresent");
        }

    }

}
