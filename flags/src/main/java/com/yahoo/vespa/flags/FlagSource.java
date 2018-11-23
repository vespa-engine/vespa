// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.Optional;

/**
 * @author hakonhall
 */
public interface FlagSource {
    /** Whether the source has the feature flag with the given id. */
    boolean hasFeature(FlagId id);

    /** The String value of a flag. */
    Optional<String> getString(FlagId id);
}
