// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.Optional;

/**
 * @author hakonhall
 */
public interface FlagSource {
    Optional<RawFlag> fetch(FlagId id, FetchVector vector);
}
