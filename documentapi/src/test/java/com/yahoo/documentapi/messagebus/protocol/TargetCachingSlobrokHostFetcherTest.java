// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.messagebus.routing.RoutingContext;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author vekterli
 */
public class TargetCachingSlobrokHostFetcherTest {

    private static String idOfIndex(int index) {
        return String.format("storage/cluster.foo/distributor/%d/default", index);
    }

    private static String idOfWildcardLookup() {
        return "storage/cluster.foo/distributor/*/default";
    }

    private static String lookupSpecOfIndex(int index) {
        return String.format("tcp/localhost:%d", index);
    }

    private static String resolvedSpecOfIndex(int index) {
        return String.format("tcp/localhost:%d/default", index);
    }

    private static List<Mirror.Entry> dummyEntries(int... indices) {
        return Arrays.stream(indices)
                .mapToObj(index -> new Mirror.Entry(idOfIndex(index), lookupSpecOfIndex(index))).collect(Collectors.toList());
    }

    static class Fixture {
        SlobrokPolicy mockSlobrokPolicy = mock(SlobrokPolicy.class);
        IMirror mockMirror = mock(IMirror.class);
        ContentPolicy.SlobrokHostPatternGenerator patternGenerator = new ContentPolicy.SlobrokHostPatternGenerator("foo");
        ContentPolicy.TargetCachingSlobrokHostFetcher hostFetcher = new ContentPolicy.TargetCachingSlobrokHostFetcher(patternGenerator, mockSlobrokPolicy, 60);
        RoutingContext routingContext = mock(RoutingContext.class);

        Fixture() {
            when(mockMirror.updates()).thenReturn(1);
            when(routingContext.getMirror()).thenReturn(mockMirror);
            when(mockSlobrokPolicy.lookup(any(), eq(idOfIndex(1)))).thenReturn(dummyEntries(1));
            when(mockSlobrokPolicy.lookup(any(), eq(idOfIndex(2)))).thenReturn(dummyEntries(2));
            when(mockSlobrokPolicy.lookup(any(), eq(idOfWildcardLookup()))).thenReturn(dummyEntries(1, 2, 3, 4));
        }
    }

    @Test
    public void lookup_passed_through_on_first_fetch() {
        Fixture fixture = new Fixture();

        String spec = fixture.hostFetcher.getTargetSpec(1, fixture.routingContext);
        assertEquals(resolvedSpecOfIndex(1), spec);
        verify(fixture.mockSlobrokPolicy, times(1)).lookup(any(), eq(idOfIndex(1)));
    }

    @Test
    public void cached_index_does_not_do_slobrok_lookup() {
        Fixture fixture = new Fixture();

        String spec1 = fixture.hostFetcher.getTargetSpec(1, fixture.routingContext);
        String spec2 = fixture.hostFetcher.getTargetSpec(1, fixture.routingContext);
        assertEquals(spec1, spec2);
        // Only invoked once
        verify(fixture.mockSlobrokPolicy, times(1)).lookup(any(), anyString());
    }

    @Test
    public void multiple_indexes_are_cached() {
        Fixture fixture = new Fixture();

        String spec1_1 = fixture.hostFetcher.getTargetSpec(1, fixture.routingContext);
        String spec2_1 = fixture.hostFetcher.getTargetSpec(2, fixture.routingContext);

        assertEquals(resolvedSpecOfIndex(1), spec1_1);
        assertEquals(resolvedSpecOfIndex(2), spec2_1);

        String spec1_2 = fixture.hostFetcher.getTargetSpec(1, fixture.routingContext);
        String spec2_2 = fixture.hostFetcher.getTargetSpec(2, fixture.routingContext);
        assertEquals(spec1_1, spec1_2);
        assertEquals(spec2_1, spec2_2);

        verify(fixture.mockSlobrokPolicy, times(1)).lookup(any(), eq(idOfIndex(1)));
        verify(fixture.mockSlobrokPolicy, times(1)).lookup(any(), eq(idOfIndex(2)));
    }

    @Test
    public void generation_change_evicts_cache() {
        Fixture fixture = new Fixture();

        when(fixture.mockMirror.updates()).thenReturn(1).thenReturn(2);
        when(fixture.mockSlobrokPolicy.lookup(any(), eq(idOfIndex(1))))
                .thenReturn(dummyEntries(1)).thenReturn(dummyEntries(2));

        String spec1 = fixture.hostFetcher.getTargetSpec(1, fixture.routingContext);
        String spec2 = fixture.hostFetcher.getTargetSpec(1, fixture.routingContext);

        assertEquals(resolvedSpecOfIndex(1), spec1);
        assertEquals(resolvedSpecOfIndex(2), spec2);
    }

    @Test
    public void wildcard_null_distributor_index_is_not_cached() {
        Fixture fixture = new Fixture();

        String spec = fixture.hostFetcher.getTargetSpec(null, fixture.routingContext);
        assertNotNull(spec);
        spec = fixture.hostFetcher.getTargetSpec(null, fixture.routingContext);
        assertNotNull(spec);

        verify(fixture.mockSlobrokPolicy, times(2)).lookup(any(), eq(idOfWildcardLookup()));
    }

}
