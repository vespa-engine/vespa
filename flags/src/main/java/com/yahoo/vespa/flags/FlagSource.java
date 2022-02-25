// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.Optional;

/**
 * A source of raw flag values that can be converted to typed flag values elsewhere.
 *
 * @author hakonhall
 */
public interface FlagSource {

    /** Returns the raw flag for the given vector (specifying hostname, application id, etc). */
    Optional<RawFlag> fetch(FlagId id, FetchVector vector);

}
