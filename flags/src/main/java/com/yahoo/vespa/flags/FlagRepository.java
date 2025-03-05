// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.flags.json.FlagData;

import java.util.Map;

/**
 * @author hakonhall
 */
public interface FlagRepository {
    Map<FlagId, FlagData> getAllFlagData();
}
