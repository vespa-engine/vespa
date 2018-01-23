// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task;

import com.yahoo.vespa.hosted.node.admin.io.FileSystem;
import org.junit.Before;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class TaskTestBase {
    protected final FileSystem fileSystemMock = mock(FileSystem.class);
    protected final Task.TaskContext contextMock = mock(Task.TaskContext.class);

    @Before
    public void baseSetup() {
        when(contextMock.getFileSystem()).thenReturn(fileSystemMock);
        when(fileSystemMock.withPath(any())).thenCallRealMethod();
        setUp();
    }

    /**
     * Override this to set up before each test.
     */
    void setUp() {}
}
