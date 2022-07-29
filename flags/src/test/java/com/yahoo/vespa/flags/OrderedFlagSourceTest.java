// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class OrderedFlagSourceTest {
    @Test
    void test() {
        FlagSource source1 = mock(FlagSource.class);
        FlagSource source2 = mock(FlagSource.class);
        OrderedFlagSource orderedSource = new OrderedFlagSource(source1, source2);

        FlagId id = new FlagId("id");
        FetchVector vector = new FetchVector();

        when(source1.fetch(any(), any())).thenReturn(Optional.empty());
        when(source2.fetch(any(), any())).thenReturn(Optional.empty());
        assertFalse(orderedSource.fetch(id, vector).isPresent());
        verify(source1, times(1)).fetch(any(), any());
        verify(source2, times(1)).fetch(any(), any());

        RawFlag rawFlag = mock(RawFlag.class);

        when(source1.fetch(any(), any())).thenReturn(Optional.empty());
        when(source2.fetch(any(), any())).thenReturn(Optional.of(rawFlag));
        assertEquals(orderedSource.fetch(id, vector), Optional.of(rawFlag));
        verify(source1, times(2)).fetch(any(), any());
        verify(source2, times(2)).fetch(any(), any());

        when(source1.fetch(any(), any())).thenReturn(Optional.of(rawFlag));
        when(source2.fetch(any(), any())).thenReturn(Optional.empty());
        assertEquals(orderedSource.fetch(id, vector), Optional.of(rawFlag));
        verify(source1, times(3)).fetch(any(), any());
        // Not invoked as source1 provided raw flag
        verify(source2, times(2)).fetch(any(), any());
    }
}