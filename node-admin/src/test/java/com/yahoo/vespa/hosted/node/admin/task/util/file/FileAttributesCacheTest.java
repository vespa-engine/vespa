// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class FileAttributesCacheTest {
    @Test
    void exists() {
        UnixPath unixPath = mock(UnixPath.class);
        FileAttributesCache cache = new FileAttributesCache(unixPath);

        when(unixPath.getAttributesIfExists()).thenReturn(Optional.empty());
        assertFalse(cache.get().isPresent());
        verify(unixPath, times(1)).getAttributesIfExists();
        verifyNoMoreInteractions(unixPath);

        FileAttributes attributes = new FileAttributes(Instant.EPOCH, 0, 0, "", false, false, 0, 0, 0);
        when(unixPath.getAttributesIfExists()).thenReturn(Optional.of(attributes));
        when(unixPath.getAttributesIfExists()).thenReturn(Optional.of(attributes));
        assertTrue(cache.get().isPresent());
        verify(unixPath, times(1 + 1)).getAttributesIfExists();
        verifyNoMoreInteractions(unixPath);

        assertEquals(attributes, cache.getOrThrow());
        verifyNoMoreInteractions(unixPath);
    }
}
