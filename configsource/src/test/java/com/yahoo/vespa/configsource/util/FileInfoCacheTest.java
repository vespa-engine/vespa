// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class FileInfoCacheTest {
    @Test
    public void basics() throws IOException {
        Path path = Paths.get("/foo");
        FilesApi filesApi = mock(FilesApi.class);
        FileInfoCache cache = new FileInfoCache(path, filesApi);

        int numExistsCalls = 0;
        int numLastModifiedTimeCalls = 0;
        int numReadAllBytesCalls = 0;

        // File does not exist and FileInfoCache has no info about it.

        when(filesApi.exists(path)).thenReturn(false);

        Optional<FileInfo> info = cache.syncAndGet();
        assertTrue(info.isPresent());
        assertFalse(info.get().exists());

        verify(filesApi, times(++numExistsCalls)).exists(path);
        verify(filesApi, times(numLastModifiedTimeCalls)).getLastModifiedTime(path);
        verify(filesApi, times(numReadAllBytesCalls)).readAllBytes(path);
        verifyNoMoreInteractions(filesApi);

        // File does not exist and FileInfoCache DO have info about it.

        when(filesApi.exists(path)).thenReturn(false);

        info = cache.syncAndGet();
        assertTrue(info.isPresent());
        assertFalse(info.get().exists());

        verify(filesApi, times(++numExistsCalls)).exists(path);
        verify(filesApi, times(numLastModifiedTimeCalls)).getLastModifiedTime(path);
        verify(filesApi, times(numReadAllBytesCalls)).readAllBytes(path);
        verifyNoMoreInteractions(filesApi);

        // File now exists

        Instant modifiedTime = Instant.ofEpochSecond(1);
        byte[] content = "content".getBytes();
        when(filesApi.exists(path)).thenReturn(true);
        when(filesApi.getLastModifiedTime(path)).thenReturn(modifiedTime);
        when(filesApi.readAllBytes(path)).thenReturn(content);

        info = cache.syncAndGet();
        assertTrue(info.isPresent());
        assertTrue(info.get().exists());
        assertEquals(modifiedTime, info.get().lastModifiedTime());
        assertEquals(content, info.get().content());

        verify(filesApi, times(++numExistsCalls)).exists(path);
        verify(filesApi, times(++numLastModifiedTimeCalls)).getLastModifiedTime(path);
        verify(filesApi, times(++numReadAllBytesCalls)).readAllBytes(path);
        verifyNoMoreInteractions(filesApi);

        // No modifications => only one additional lastModifiedTime

        Optional<FileInfo> newInfo = cache.syncAndGet();
        assertTrue(newInfo.isPresent());
        assertTrue(info.get().exists());
        assertEquals(modifiedTime, info.get().lastModifiedTime());
        assertEquals(content, info.get().content());

        verify(filesApi, times(numExistsCalls)).exists(path);
        verify(filesApi, times(++numLastModifiedTimeCalls)).getLastModifiedTime(path);
        verify(filesApi, times(numReadAllBytesCalls)).readAllBytes(path);
        verifyNoMoreInteractions(filesApi);

        // New last modified time, but content hasn't actually changed.

        Instant newModifiedTime = modifiedTime.plus(Duration.ofSeconds(1));
        when(filesApi.getLastModifiedTime(path)).thenReturn(newModifiedTime);

        newInfo = cache.syncAndGet();
        assertTrue(newInfo.isPresent());
        assertTrue(info.get().exists());
        assertEquals(modifiedTime, info.get().lastModifiedTime());
        assertEquals(content, info.get().content());

        verify(filesApi, times(numExistsCalls)).exists(path);
        verify(filesApi, times(++numLastModifiedTimeCalls)).getLastModifiedTime(path);
        verify(filesApi, times(++numReadAllBytesCalls)).readAllBytes(path);
        verifyNoMoreInteractions(filesApi);

        // New last modified time with new content.

        byte[] newContent = "new content".getBytes();
        when(filesApi.readAllBytes(path)).thenReturn(newContent);
        newModifiedTime = newModifiedTime.plus(Duration.ofSeconds(1));
        when(filesApi.getLastModifiedTime(path)).thenReturn(newModifiedTime);

        newInfo = cache.syncAndGet();
        assertTrue(newInfo.isPresent());
        assertTrue(newInfo.get().exists());
        assertEquals(newModifiedTime, newInfo.get().lastModifiedTime());
        assertEquals(newContent, newInfo.get().content());

        verify(filesApi, times(numExistsCalls)).exists(path);
        verify(filesApi, times(++numLastModifiedTimeCalls)).getLastModifiedTime(path);
        verify(filesApi, times(++numReadAllBytesCalls)).readAllBytes(path);
        verifyNoMoreInteractions(filesApi);

        // IO error causes no snapshot change

        Instant newestModifiedTime = newModifiedTime.plus(Duration.ofSeconds(1));
        when(filesApi.getLastModifiedTime(path)).thenReturn(newestModifiedTime);
        when(filesApi.readAllBytes(path)).thenThrow(new NotDirectoryException("bar"));

        newInfo = cache.syncAndGet();
        assertTrue(newInfo.isPresent());
        assertTrue(newInfo.get().exists());
        assertEquals(newModifiedTime, newInfo.get().lastModifiedTime());
        assertEquals(newContent, newInfo.get().content());

        verify(filesApi, times(numExistsCalls)).exists(path);
        verify(filesApi, times(++numLastModifiedTimeCalls)).getLastModifiedTime(path);
        verify(filesApi, times(++numReadAllBytesCalls)).readAllBytes(path);
        verifyNoMoreInteractions(filesApi);
    }

    @Test
    public void testFileExistsFirstTime() throws IOException {
        Path path = Paths.get("/foo");
        FilesApi filesApi = mock(FilesApi.class);
        FileInfoCache cache = new FileInfoCache(path, filesApi);

        int numExistsCalls = 0;
        int numLastModifiedTimeCalls = 0;
        int numReadAllBytesCalls = 0;

        // File exist but FileInfoCache has no info about it.

        Instant modifiedTime = Instant.ofEpochSecond(1);
        byte[] content = "content".getBytes();
        when(filesApi.exists(path)).thenReturn(true);
        when(filesApi.getLastModifiedTime(path)).thenReturn(modifiedTime);
        when(filesApi.readAllBytes(path)).thenReturn(content);

        Optional<FileInfo> info = cache.syncAndGet();
        assertTrue(info.isPresent());
        assertTrue(info.get().exists());
        assertEquals(modifiedTime, info.get().lastModifiedTime());
        assertEquals(content, info.get().content());

        verify(filesApi, times(++numExistsCalls)).exists(path);
        verify(filesApi, times(++numLastModifiedTimeCalls)).getLastModifiedTime(path);
        verify(filesApi, times(++numReadAllBytesCalls)).readAllBytes(path);
        verifyNoMoreInteractions(filesApi);
    }
}
