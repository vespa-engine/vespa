// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ServiceNameUtilTest {
    @Test
    public void testConversionToConfigId() {
        final Set<String> slobrokServices = new HashSet<>();
        slobrokServices.add("storage/cluster.basicsearch/storage/0");
        slobrokServices.add("storage/cluster.basicsearch/distributor/0");
        slobrokServices.add("docproc/cluster.basicsearch.indexing/0/chain.indexing");
        slobrokServices.add("storage/cluster.basicsearch/distributor/0/default");
        slobrokServices.add("storage/cluster.basicsearch/storage/0/default");
        slobrokServices.add("basicsearch/search/cluster.basicsearch/0/realtimecontroller");

        final Set<String> configIds = ServiceNameUtil.convertSlobrokServicesToConfigIds(slobrokServices);

        assertThat(configIds,
                is(setOf(
                        "basicsearch/search/cluster.basicsearch/0",
                        "basicsearch/distributor/0",
                        "basicsearch/storage/0",
                        "docproc/cluster.basicsearch.indexing/0")));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static <T> Set<T> setOf(T... t) {
        return new HashSet<>(Arrays.asList(t));
    }
}
