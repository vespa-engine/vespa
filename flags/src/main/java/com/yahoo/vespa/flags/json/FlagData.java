// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.vespa.flags.Deserializer;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.RawFlag;
import com.yahoo.vespa.flags.json.wire.WireFlagData;
import com.yahoo.vespa.flags.json.wire.WireFlagDataList;
import com.yahoo.vespa.flags.json.wire.WireRule;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A data structure containing all data for a single flag, that can be serialized to/from JSON,
 * and that can be used to implement {@link FlagSource}.
 *
 * @author hakonhall
 */
public class FlagData {
    private final FlagId id;
    private final List<Rule> rules;
    private final FetchVector defaultFetchVector;

    public FlagData(FlagId id) {
        this(id, new FetchVector(), List.of());
    }

    public FlagData(FlagId id, FetchVector defaultFetchVector, Rule... rules) {
        this(id, defaultFetchVector, List.of(rules));
    }

    public FlagData(FlagId id, FetchVector defaultFetchVector, List<Rule> rules) {
        this.id = id;
        this.rules = List.copyOf(rules);
        this.defaultFetchVector = defaultFetchVector;
    }

    public FlagId id() {
        return id;
    }

    public List<Rule> rules() {
        return rules;
    }

    public boolean isEmpty() { return rules.isEmpty() && defaultFetchVector.isEmpty(); }

    public FlagData partialResolve(FetchVector fetchVector) {
        // Note:  As a result of partialResolve, there could be e.g. two identical rules, and the latter will always be ignored by resolve().
        // Consider deduping.  Deduping is actually not specific to partialResolve and could be done e.g. at construction time.

        List<Rule> newRules = new ArrayList<>();
        for (var rule : rules) {
            Optional<Rule> partialRule = rule.partialResolve(fetchVector);
            if (partialRule.isPresent()) {
                newRules.add(partialRule.get());
                if (partialRule.get().conditions().isEmpty()) {
                    // Any following rule will always be ignored during resolution.
                    break;
                }
            }
        }
        newRules = optimizeRules(newRules);

        FetchVector newDefaultFetchVector = defaultFetchVector.without(fetchVector.dimensions());

        return new FlagData(id, newDefaultFetchVector, newRules);
    }

    public Optional<RawFlag> resolve(FetchVector fetchVector) {
        return rules.stream()
                .filter(rule -> rule.match(defaultFetchVector.with(fetchVector)))
                .findFirst()
                .flatMap(Rule::getValueToApply);
    }

    public String serializeToJson() {
        return toWire().serializeToJson();
    }

    public byte[] serializeToUtf8Json() {
        return toWire().serializeToBytes();
    }

    public void serializeToOutputStream(OutputStream outputStream) {
        toWire().serializeToOutputStream(outputStream);
    }

    public JsonNode toJsonNode() {
        return toWire().serializeToJsonNode();
    }

    /** Can be used with Jackson. */
    public WireFlagData toWire() {
        WireFlagData wireFlagData = new WireFlagData();

        wireFlagData.id = id.toString();

        if (!rules.isEmpty()) {
            wireFlagData.rules = rules.stream().map(Rule::toWire).toList();
        }

        wireFlagData.defaultFetchVector = FetchVectorHelper.toWire(defaultFetchVector);

        return wireFlagData;
    }

    /** E.g. verify all RawFlag can be deserialized. */
    public void validate(Deserializer<?> deserializer) {
        rules.stream()
             .flatMap(rule -> rule.getValueToApply().map(Stream::of).orElse(null))
             .forEach(deserializer::deserialize);

    }

    @Override
    public String toString() {
        return "FlagData{" +
               "id=" + id +
               ", rules=" + rules +
               ", defaultFetchVector=" + defaultFetchVector +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlagData flagData = (FlagData) o;
        return id.equals(flagData.id) && rules.equals(flagData.rules) && defaultFetchVector.equals(flagData.defaultFetchVector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, rules, defaultFetchVector);
    }

    public static FlagData deserializeUtf8Json(byte[] bytes) {
        return fromWire(WireFlagData.deserialize(bytes));
    }

    public static FlagData deserialize(InputStream inputStream) {
        return fromWire(WireFlagData.deserialize(inputStream));
    }

    public static FlagData deserialize(String string) {
        return fromWire(WireFlagData.deserialize(string));
    }

    /** Can be used with Jackson. */
    public static FlagData fromWire(WireFlagData wireFlagData) {
        if (wireFlagData.id == null) {
            throw new IllegalArgumentException("Flag ID missing");
        }

        return new FlagData(
                new FlagId(wireFlagData.id),
                FetchVectorHelper.fromWire(wireFlagData.defaultFetchVector),
                rulesFromWire(wireFlagData.rules)
        );
    }

    public static byte[] serializeListToUtf8Json(List<FlagData> list) {
        return listToWire(list).serializeToBytes();
    }

    public static List<FlagData> deserializeList(byte[] bytes) {
        return listFromWire(WireFlagDataList.deserializeFrom(bytes));
    }

    public static WireFlagDataList listToWire(List<FlagData> list) {
        WireFlagDataList wireList = new WireFlagDataList();
        wireList.flags = list.stream().map(FlagData::toWire).toList();
        return wireList;
    }

    public static List<FlagData> listFromWire(WireFlagDataList wireList) {
        return wireList.flags.stream().map(FlagData::fromWire).toList();
    }

    private static List<Rule> rulesFromWire(List<WireRule> wireRules) {
        if (wireRules == null) return List.of();
        return optimizeRules(wireRules.stream().map(Rule::fromWire).toList());
    }

    /** Take a raw list of rules from e.g. deserialization or partial resolution and normalize/simplify it. */
    private static List<Rule> optimizeRules(List<Rule> rules) {
        // Remove trailing rules without value, as absent value implies the code default.
        // Removing trailing rules may further simplify when e.g. this results in no rules,
        // which is equivalent to no flag data at all, and flag data may be deleted from a zone.
        if (rules.isEmpty()) return rules;
        if (rules.get(rules.size() - 1).getValueToApply().isPresent()) return rules;
        var newRules = new ArrayList<>(rules);
        while (newRules.size() > 0) {
            Rule lastRule = newRules.get(newRules.size() - 1);
            if (lastRule.getValueToApply().isEmpty()) {
                newRules.remove(newRules.size() - 1);
            } else {
                break;
            }
        }
        return newRules;
    }
}

