// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class FileAttributesCacheTest {
    @Test
    public void exists() {
        UnixPath unixPath = mock(UnixPath.class);
        FileAttributesCache cache = new FileAttributesCache(unixPath);

        when(unixPath.getAttributesIfExists()).thenReturn(Optional.empty());
        assertFalse(cache.exists());
        verify(unixPath, times(1)).getAttributesIfExists();
        verifyNoMoreInteractions(unixPath);

        FileAttributes attributes = mock(FileAttributes.class);
        when(unixPath.getAttributesIfExists()).thenReturn(Optional.of(attributes));
        assertTrue(cache.exists());
        verify(unixPath, times(1 + 1)).getAttributesIfExists();
        verifyNoMoreInteractions(unixPath);

        assertEquals(attributes, cache.get());
        verifyNoMoreInteractions(unixPath);
    }
}