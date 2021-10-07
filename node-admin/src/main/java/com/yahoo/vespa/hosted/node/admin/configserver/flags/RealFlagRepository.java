// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.flags;

import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.FlagRepository;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.flags.json.wire.WireFlagDataList;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author hakonhall
 */
public class RealFlagRepository implements FlagRepository {
    private final ConfigServerApi configServerApi;

    public RealFlagRepository(ConfigServerApi configServerApi) {
        this.configServerApi = configServerApi;
    }

    @Override
    public Map<FlagId, FlagData> getAllFlagData() {
        WireFlagDataList list = configServerApi.get("/flags/v1/data?recursive=true", WireFlagDataList.class);
        return FlagData.listFromWire(list).stream().collect(Collectors.toMap(FlagData::id, Function.identity()));
    }
}
