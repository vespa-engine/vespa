// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.fs4.MapEncoder;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.Ranking;
import com.yahoo.tensor.Tensor;
import com.yahoo.text.JSON;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Contains the rank features of a query.
 *
 * @author bratseth
 */
public class RankFeatures implements Cloneable {

    private final Ranking parent;
    private final Map<String, Object> features;
    /// This caches the
    private static final Map<String, CompoundName> compoundNameCache = new CopyOnWriteHashMap<>();

    public RankFeatures(Ranking parent) {
        this(parent, new LinkedHashMap<>());
    }

    private RankFeatures(Ranking parent, Map<String, Object> features) {
        this.parent = parent;
        this.features = features;
    }

    /** Sets a double rank feature */
    public void put(String name, double value) {
        features.put(name, value);
    }

    /** Sets a tensor rank feature */
    public void put(String name, Tensor value) {
        verifyType(name, value);
        if (value.type().rank() == 0)
            features.put(name, value.asDouble());
        else
            features.put(name, value);
    }

    private void verifyType(String name, Object value) {
        parent.getParent().properties().requireSettable(
                compoundNameCache.computeIfAbsent(name, (key) -> new CompoundName(List.of("ranking", "features", key))),
                value, Map.of());
    }

    /**
     * Sets a rank feature to a string. This will be available as the hash value
     * of the string in ranking, so it can be used in equality comparisons
     * with other string, but not for any other purpose.
     */
    public void put(String name, String value) {
        features.put(name, value);
    }

    /** Returns this value as either a Double, Tensor or String. Returns null if the value is not set. */
    public Object getObject(String name) {
        return features.get(name);
    }

    /**
     * Returns a double rank feature, or empty if there is no value with this name.
     *
     * @throws IllegalArgumentException if the value is set but is not a double
     */
    public OptionalDouble getDouble(String name) {
        Object feature = features.get(name);
        if (feature == null) return OptionalDouble.empty();
        if (feature instanceof Double) return OptionalDouble.of((Double)feature);
        throw new IllegalArgumentException("Expected '" + name + "' to be a double, but it is " +
                                           (feature instanceof Tensor ? "the tensor " + ((Tensor)feature).toAbbreviatedString() :
                                            "the string '" + feature + "'"));
    }

    /**
     * Returns a rank feature as a tensor, or empty if there is no value with this name.
     *
     * @throws IllegalArgumentException if the value is a string, not a tensor or double
     */
    public Optional<Tensor> getTensor(String name) {
        Object feature = features.get(name);
        if (feature == null) return Optional.empty();
        if (feature instanceof Tensor) return Optional.of((Tensor)feature);
        if (feature instanceof Double) return Optional.of(Tensor.from((Double)feature));
        throw new IllegalArgumentException("Expected '" + name + "' to be a tensor, but it is the string '" + feature + "'");
    }

    /**
     * Returns a rank feature as a string, or empty if there is no value with this name.
     *
     * @throws IllegalArgumentException if the value is a tensor or double, not a string
     */
    public Optional<String> getString(String name) {
        Object feature = features.get(name);
        if (feature == null) return Optional.empty();
        if (feature instanceof String) return Optional.of((String)feature);
        throw new IllegalArgumentException("Expected '" + name + "' to be a string, but it is " +
                                           (feature instanceof Tensor ? "the tensor " + ((Tensor)feature).toAbbreviatedString() :
                                                                        "the double " + feature));
    }


    /**
     * Returns the map holding the features of this.
     * This map may be modified to change the rank features of the query.
     */
    public Map<String, Object> asMap() { return features; }

    public boolean isEmpty() {
        return features.isEmpty();
    }

    /**
     * Prepares this for encoding, not for external use. See encode on Query for details.
     * <p>
     * If the query feature is found in the rank feature set,
     * remove all these entries and insert them into the rank property set instead.
     * We want to hide from the user that the query feature value is sent down as a rank property
     * and picked up by the query feature executor in the backend.
     */
    public void prepare(RankProperties rankProperties) {
        if (isEmpty()) return;

        List<String> featuresToRemove = new ArrayList<>();
        List<String> propertiesToInsert = new ArrayList<>();
        for (String key : features.keySet()) {
            if (key.startsWith("query(") && key.endsWith(")")) {
                featuresToRemove.add(key);
                propertiesToInsert.add(key.substring("query(".length(), key.length() - 1));
            } else if (key.startsWith("$")) {
                featuresToRemove.add(key);
                propertiesToInsert.add(key.substring(1));
            }
        }
        for (int i = 0; i < featuresToRemove.size(); ++i) {
            rankProperties.put(propertiesToInsert.get(i), features.remove(featuresToRemove.get(i)));
        }
    }

    public int encode(ByteBuffer buffer) {
        return MapEncoder.encodeMap("feature", features, buffer);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof RankFeatures)) return false;

        return this.features.equals(((RankFeatures)other).features);
    }

    @Override
    public int hashCode() {
        return features.hashCode();
    }

    @Override
    public RankFeatures clone() {
        return new RankFeatures(parent, new LinkedHashMap<>(features));
    }

    public RankFeatures cloneFor(Ranking parent) {
        return new RankFeatures(parent, new LinkedHashMap<>(features));
    }

    @Override
    public String toString() {
        return JSON.encode(features);
    }

    // See also QueryPropertyAliases
    public static boolean isFeatureName(String fullPropertyName) {
        return fullPropertyName.startsWith("ranking.features.") ||
               fullPropertyName.startsWith("rankfeature.") ||
               fullPropertyName.startsWith("featureoverride.") ||
               fullPropertyName.startsWith("input.");
    }

}
