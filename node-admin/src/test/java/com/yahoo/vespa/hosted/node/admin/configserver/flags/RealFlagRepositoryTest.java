// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.flags;

import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.flags.json.wire.WireFlagData;
import com.yahoo.vespa.flags.json.wire.WireFlagDataList;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import org.hamcrest.collection.IsMapContaining;
import org.hamcrest.collection.IsMapWithSize;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
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
    public void test() {
        WireFlagDataList list = new WireFlagDataList();
        list.flags = new ArrayList<>();
        list.flags.add(new WireFlagData());
        list.flags.get(0).id = "id1";

        when(configServerApi.get(any(), eq(WireFlagDataList.class))).thenReturn(list);
        Map<FlagId, FlagData> allFlagData = repository.getAllFlagData();
        assertThat(allFlagData, IsMapWithSize.aMapWithSize(1));
        assertThat(allFlagData, IsMapContaining.hasKey(new FlagId("id1")));
    }
}