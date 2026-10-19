// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.api.annotations.Beta;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Requires matching rows in every clause to agree on their shared variables.
 * A variable omitted by a clause is unbound there: a unit-only clause can match
 * the same unit as clauses which also bind a zone.
 *
 */
@Beta
public class CorrelateByItem extends NonReducibleCompositeItem {

    private static final String BINDINGS = "vespa.correlateBy.bindings";
    private static final String EXACT_VALUES = "vespa.correlateBy.exactValues";
    private static final String FIRST_PRESENT = "vespa.correlateBy.firstPresent";
    private static final String PRESENCE = "vespa.correlateBy.presence";
    private Map<String, List<String>> allowedValues = Map.of();
    private int nextPresenceId;

    private record PresenceSelection(int id, String variable, List<String> values) {}

    /** A value-filtered clause restricted by a separate, ordered presence query. */
    public record FirstPresentRule(int clause, int presence, String variable, List<String> values) {}

    /** Adds a clause and its variable-to-struct-field bindings. Fields are relative to the struct. */
    public void addClause(SameElementItem clause, Map<String, String> bindings) {
        addClause(clause, bindings, Map.of());
    }

    /** Adds a clause with exact string values required on its matching struct rows. */
    public void addClause(SameElementItem clause, Map<String, String> bindings, Map<String, List<String>> exactValues) {
        if (bindings.isEmpty()) throw new IllegalArgumentException("A correlated clause must bind at least one variable");
        for (var binding : bindings.entrySet()) {
            if (binding.getKey().isBlank() || binding.getValue().isBlank())
                throw new IllegalArgumentException("Binding variables and fields must be nonempty");
        }
        clause.addAnnotation(BINDINGS, Map.copyOf(bindings));
        var copied = new TreeMap<String, List<String>>();
        exactValues.forEach((field, values) -> {
            if (field.isBlank()) throw new IllegalArgumentException("Exact value fields must be nonempty");
            copied.put(field, List.copyOf(values));
        });
        clause.addAnnotation(EXACT_VALUES, Map.copyOf(copied));
        addItem(clause);
    }

    /** Returns immutable bindings for the clause at the given position. */
    @SuppressWarnings("unchecked")
    public Map<String, String> getBindings(int clause) {
        Object bindings = getItem(clause).getAnnotation(BINDINGS);
        if (!(bindings instanceof Map<?, ?>))
            throw new IllegalArgumentException("Every correlateBy clause must have variable bindings");
        return (Map<String, String>) bindings;
    }

    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getExactValues(int clause) {
        Object values = getItem(clause).getAnnotation(EXACT_VALUES);
        return values == null ? Map.of() : (Map<String, List<String>>) values;
    }

    /**
     * Adds a clause evaluated at the first listed value present for its other bindings.
     * The presence query must bind the same variables on the same struct fields,
     * and should test key presence independently of the matching value predicate.
     */
    public void addFirstPresentClause(SameElementItem clause, SameElementItem presence,
                                      Map<String, String> bindings, Map<String, List<String>> exactValues,
                                      Map<String, List<String>> presenceExactValues,
                                      String variable, List<String> values) {
        if (!bindings.containsKey(variable)) throw new IllegalArgumentException("First-present variable must be bound by the clause");
        if (!clause.getFieldName().equals(presence.getFieldName()) || clause == presence)
            throw new IllegalArgumentException("First-present queries must be distinct clauses on the same struct field");
        var selection = new PresenceSelection(nextPresenceId++, variable, List.copyOf(values));
        addClause(clause, bindings, exactValues);
        addClause(presence, bindings, presenceExactValues);
        clause.addAnnotation(FIRST_PRESENT, selection);
        presence.addAnnotation(PRESENCE, selection.id());
    }

    public List<FirstPresentRule> getFirstPresentRules() {
        var presenceClauses = new TreeMap<Integer, Integer>();
        for (int i = 0; i < getItemCount(); ++i) {
            if (getItem(i).getAnnotation(PRESENCE) instanceof Integer id && presenceClauses.putIfAbsent(id, i) != null)
                throw new IllegalArgumentException("First-present presence clauses must be unique");
        }
        var rules = new ArrayList<FirstPresentRule>();
        var used = new HashSet<Integer>();
        for (int i = 0; i < getItemCount(); ++i) {
            if (!(getItem(i).getAnnotation(FIRST_PRESENT) instanceof PresenceSelection selection)) continue;
            Integer presence = presenceClauses.get(selection.id());
            if (presence == null || !used.add(presence) || presence == i ||
                getItem(i).getAnnotation(PRESENCE) != null || !getBindings(i).equals(getBindings(presence)))
                throw new IllegalArgumentException("First-present clause has no unique compatible presence query");
            rules.add(new FirstPresentRule(i, presence, selection.variable(), selection.values()));
        }
        if (used.size() != presenceClauses.size()) throw new IllegalArgumentException("First-present presence query has no matching clause");
        return List.copyOf(rules);
    }

    /** Restricts a string variable to these values, using exact equality. An empty list matches nothing. */
    public void setAllowedValues(String variable, List<String> values) {
        if (variable.isBlank()) throw new IllegalArgumentException("Binding variable must be nonempty");
        var updated = new TreeMap<>(allowedValues);
        updated.put(variable, List.copyOf(values));
        allowedValues = Map.copyOf(updated);
    }

    public Map<String, List<String>> getAllowedValues() { return allowedValues; }

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
        buffer.append(getName()).append(allowedValues).append(' ');
        if (!getFirstPresentRules().isEmpty()) buffer.append(getFirstPresentRules()).append(' ');
    }

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        for (int i = 0; i < getItemCount(); ++i) {
            if (i > 0) buffer.append(' ');
            buffer.append(new TreeMap<>(getBindings(i))).append(':').append(getItem(i));
        }
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf(SerializationContext context) {
        var firstPresentRules = getFirstPresentRules();
        if (getItemCount() == 0)
            throw new IllegalArgumentException("correlateBy requires at least one matching clause");
        var builder = SearchProtocol.ItemCorrelateBy.newBuilder();
        if (getLabel() != null) builder.setLabel(getLabel());
        for (int i = 0; i < getItemCount(); ++i) {
            if (!(getItem(i) instanceof SameElementItem))
                throw new IllegalArgumentException("correlateBy clauses must be sameElement items");
            var clause = SearchProtocol.CorrelationClause.newBuilder().setQuery(getItem(i).toProtobuf(context));
            new TreeMap<>(getBindings(i)).forEach((variable, field) -> clause.addBindings(
                    SearchProtocol.CorrelationBinding.newBuilder().setVariable(variable).setField(field)));
            new TreeMap<>(getExactValues(i)).forEach((field, values) -> clause.addExactValues(
                    SearchProtocol.CorrelationStringValues.newBuilder().setField(field).addAllValues(values)));
            builder.addClauses(clause);
        }
        new TreeMap<>(allowedValues).forEach((variable, values) -> builder.addDomains(
                SearchProtocol.CorrelationDomain.newBuilder().setVariable(variable).addAllValues(values)));
        firstPresentRules.forEach(rule -> builder.addFirstPresent(SearchProtocol.CorrelationFirstPresent.newBuilder()
                .setClause(rule.clause()).setPresence(rule.presence()).setVariable(rule.variable()).addAllValues(rule.values())));
        return SearchProtocol.QueryTreeItem.newBuilder().setItemCorrelateBy(builder).build();
    }

    @Override
    public boolean equals(Object other) {
        if (!super.equals(other)) return false;
        var that = (CorrelateByItem) other;
        if (!allowedValues.equals(that.allowedValues)) return false;
        if (!getFirstPresentRules().equals(that.getFirstPresentRules())) return false;
        for (int i = 0; i < getItemCount(); ++i) {
            if (!getBindings(i).equals(that.getBindings(i))) return false;
            if (!getExactValues(i).equals(that.getExactValues(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(super.hashCode(), allowedValues, getFirstPresentRules());
        for (int i = 0; i < getItemCount(); ++i)
            hash = 31 * hash + Objects.hash(getBindings(i), getExactValues(i));
        return hash;
    }
}
