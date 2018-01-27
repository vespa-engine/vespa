// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class FileContentCacheTest {
    private final UnixPath unixPath = mock(UnixPath.class);
    private final FileContentCache cache = new FileContentCache(unixPath);

    @Test
    public void get() throws Exception {
        when(unixPath.readUtf8File()).thenReturn("content");
        assertEquals("content", cache.get(Instant.ofEpochMilli(0)));
        verify(unixPath, times(1)).readUtf8File();
        verifyNoMoreInteractions(unixPath);

        // cache hit
        assertEquals("content", cache.get(Instant.ofEpochMilli(0)));
        verify(unixPath, times(1)).readUtf8File();
        verifyNoMoreInteractions(unixPath);

        // cache miss
        when(unixPath.readUtf8File()).thenReturn("new-content");
        assertEquals("new-content", cache.get(Instant.ofEpochMilli(1)));
        verify(unixPath, times(1 + 1)).readUtf8File();
        verifyNoMoreInteractions(unixPath);

        // cache hit both at times 0 and 1
        assertEquals("new-content", cache.get(Instant.ofEpochMilli(0)));
        verify(unixPath, times(1 + 1)).readUtf8File();
        verifyNoMoreInteractions(unixPath);
        assertEquals("new-content", cache.get(Instant.ofEpochMilli(1)));
        verify(unixPath, times(1 + 1)).readUtf8File();
        verifyNoMoreInteractions(unixPath);
    }

    @Test
    public void updateWith() throws Exception {
        cache.updateWith("content", Instant.ofEpochMilli(2));
        assertEquals("content", cache.get(Instant.ofEpochMilli(2)));
        verifyNoMoreInteractions(unixPath);

        cache.updateWith("new-content", Instant.ofEpochMilli(4));
        assertEquals("new-content", cache.get(Instant.ofEpochMilli(4)));
        verifyNoMoreInteractions(unixPath);
    }

}