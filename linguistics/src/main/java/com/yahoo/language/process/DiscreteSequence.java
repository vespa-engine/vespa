// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.api.annotations.Beta;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A discrete sequence produced by quantization or sequence generation.
 *
 * @author gdonovan
 */
@Beta
public record DiscreteSequence(List<Integer> tokenIds, List<String> tokenStrings) {

    public DiscreteSequence {
        tokenIds = List.copyOf(Objects.requireNonNull(tokenIds));
        tokenStrings = List.copyOf(Objects.requireNonNull(tokenStrings));
        if (!tokenStrings.isEmpty() && tokenStrings.size() != tokenIds.size()) {
            throw new IllegalArgumentException("tokenStrings must be empty or have the same size as tokenIds");
        }
    }

    public static DiscreteSequence of(List<Integer> tokenIds) {
        return new DiscreteSequence(tokenIds, List.of());
    }

    public boolean hasTokenStrings() {
        return !tokenStrings.isEmpty();
    }

    public String asText(String delimiter) {
        Objects.requireNonNull(delimiter);
        if (hasTokenStrings()) {
            return String.join(delimiter, tokenStrings);
        }
        return tokenIds.stream().map(String::valueOf).collect(Collectors.joining(delimiter));
    }
}
