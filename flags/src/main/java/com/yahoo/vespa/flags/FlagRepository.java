// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.flags.json.FlagData;

import java.util.Map;
import java.util.Optional;

/**
 * @author hakonhall
 */
public interface FlagRepository extends FlagSource {
    Map<FlagId, FlagData> getAllFlagData();

    @Override
    default Optional<RawFlag> fetch(FlagId id, FetchVector vector) {
        return Optional.ofNullable(getAllFlagData().get(id)).flatMap(flagData -> flagData.resolve(vector));
    }
}
