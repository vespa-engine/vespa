// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.utils;

import com.yahoo.vespa.config.search.core.FdispatchrcConfig;
import com.yahoo.vespa.config.search.core.PartitionsConfig;
import com.yahoo.vespa.model.search.Dispatch;

import static org.junit.Assert.assertEquals;

public class DispatchUtils {

    public static PartitionsConfig.Dataset getDataset(Dispatch dispatch) {
        PartitionsConfig.Builder builder = new PartitionsConfig.Builder();
        dispatch.getConfig(builder);
        PartitionsConfig cfg = new PartitionsConfig(builder);
        assertEquals(1, cfg.dataset().size());
        return cfg.dataset(0);
    }

    public static FdispatchrcConfig getFdispatchrcConfig(Dispatch dispatch) {
        FdispatchrcConfig.Builder builder = new FdispatchrcConfig.Builder();
        dispatch.getConfig(builder);
        return new FdispatchrcConfig(builder);
    }

    public static void assertEngine(int rowId, int partitionId, PartitionsConfig.Dataset.Engine engine) {
        assertEquals(rowId, engine.rowid());
        assertEquals(partitionId, engine.partid());
    }

    public static void assertEngine(int rowId, int partitionId, String connectSpec, PartitionsConfig.Dataset.Engine engine) {
        assertEngine(rowId, partitionId, engine);
        assertEquals(connectSpec, engine.name_and_port());
    }
}
