// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.metric;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Used to easily generate points (Map&lt;String, ?&gt;) for a space defined here by its dimension names.
 *
 * @author jonmv
 */
public class Space {

    private final List<String> dimensions;

    private Space(List<String> dimensions) {
        this.dimensions = dimensions;
    }

    /** Creates a new space with the given named dimensions, in order. */
    public static Space of(List<String> dimensions) {
        if (Set.copyOf(dimensions).size() != dimensions.size())
            throw new IllegalArgumentException("Duplicated dimension names in '" + dimensions + "'.");

        return new Space(List.copyOf(dimensions));
    }

    /** Returns a point in this space, with the given values along each dimensions, in order. */
    public Map<String, ?> at(List<?> values) {
        if (dimensions.size() != values.size())
            throw new IllegalArgumentException("This space has " + dimensions.size() + " dimensions, but " + values.size() + " were given.");

        return IntStream.range(0, dimensions.size()).boxed().collect(toUnmodifiableMap(dimensions::get, values::get));
    }

    /** Returns a point in this space, with the given values along each dimensions, in order. */
    public Map<String, ?> at(Object... values) {
        return at(List.of(values));
    }

}
