// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.flags;

import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.flags.json.wire.WireFlagData;
import com.yahoo.vespa.flags.json.wire.WireFlagDataList;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class RealFlagRepositoryTest {
    private final ConfigServerApi configServerApi = mock(ConfigServerApi.class);
    private final RealFlagRepository repository = new RealFlagRepository(configServerApi);

    @Test
    void test() {
        WireFlagDataList list = new WireFlagDataList();
        list.flags = new ArrayList<>();
        list.flags.add(new WireFlagData());
        list.flags.get(0).id = "id1";

        when(configServerApi.get(any(), eq(WireFlagDataList.class))).thenReturn(list);
        Map<FlagId, FlagData> allFlagData = repository.getAllFlagData();
        assertEquals(1, allFlagData.size());
        assertTrue(allFlagData.containsKey(new FlagId("id1")));
    }
}