// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
            wireFlagData.rules = rules.stream().map(Rule::toWire).collect(Collectors.toList());
        }

        wireFlagData.defaultFetchVector = FetchVectorHelper.toWire(defaultFetchVector);

        return wireFlagData;
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
        wireList.flags = list.stream().map(FlagData::toWire).collect(Collectors.toList());
        return wireList;
    }

    public static List<FlagData> listFromWire(WireFlagDataList wireList) {
        return wireList.flags.stream().map(FlagData::fromWire).collect(Collectors.toList());
    }

    private static List<Rule> rulesFromWire(List<WireRule> wireRules) {
        if (wireRules == null) return List.of();
        return wireRules.stream().map(Rule::fromWire).collect(Collectors.toList());
    }

    /** E.g. verify all RawFlag can be deserialized. */
    public void validate(Deserializer<?> deserializer) {
        rules.stream()
                .flatMap(rule -> rule.getValueToApply().map(Stream::of).orElse(null))
                .forEach(deserializer::deserialize);

    }
}

