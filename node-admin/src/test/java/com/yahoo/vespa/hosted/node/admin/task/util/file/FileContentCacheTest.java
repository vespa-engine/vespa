// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class FileContentCacheTest {
    private final UnixPath unixPath = mock(UnixPath.class);
    private final FileContentCache cache = new FileContentCache(unixPath);

    private final byte[] content = "content".getBytes(StandardCharsets.UTF_8);
    private final byte[] newContent = "new-content".getBytes(StandardCharsets.UTF_8);

    @Test
    void get() {
        when(unixPath.readBytes()).thenReturn(content);
        assertArrayEquals(content, cache.get(Instant.ofEpochMilli(0)));
        verify(unixPath, times(1)).readBytes();
        verifyNoMoreInteractions(unixPath);

        // cache hit
        assertArrayEquals(content, cache.get(Instant.ofEpochMilli(0)));
        verify(unixPath, times(1)).readBytes();
        verifyNoMoreInteractions(unixPath);

        // cache miss
        when(unixPath.readBytes()).thenReturn(newContent);
        assertArrayEquals(newContent, cache.get(Instant.ofEpochMilli(1)));
        verify(unixPath, times(1 + 1)).readBytes();
        verifyNoMoreInteractions(unixPath);

        // cache hit both at times 0 and 1
        assertArrayEquals(newContent, cache.get(Instant.ofEpochMilli(0)));
        verify(unixPath, times(1 + 1)).readBytes();
        verifyNoMoreInteractions(unixPath);
        assertArrayEquals(newContent, cache.get(Instant.ofEpochMilli(1)));
        verify(unixPath, times(1 + 1)).readBytes();
        verifyNoMoreInteractions(unixPath);
    }

    @Test
    void updateWith() {
        cache.updateWith(content, Instant.ofEpochMilli(2));
        assertArrayEquals(content, cache.get(Instant.ofEpochMilli(2)));
        verifyNoMoreInteractions(unixPath);

        cache.updateWith(newContent, Instant.ofEpochMilli(4));
        assertArrayEquals(newContent, cache.get(Instant.ofEpochMilli(4)));
        verifyNoMoreInteractions(unixPath);
    }

}