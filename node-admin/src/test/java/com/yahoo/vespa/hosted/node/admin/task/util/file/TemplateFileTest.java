// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * WARNING: Velocity does not honor an alternative FileSystem like JimFS.
 */
public class TemplateFileTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private void writeFile(Path path, String content) {
        UnixPath unixPath = new UnixPath(path);
        unixPath.createParents();
        unixPath.writeUtf8File(content);
    }

    @Test
    public void basic() throws IOException {
        String templateContent = "a $x, $y b";
        Path templatePath = folder.newFile("example.vm").toPath();
        writeFile(templatePath, templateContent);

        Path toPath = folder.newFile().toPath();
        TaskContext taskContext = mock(TaskContext.class);
        boolean converged = new TemplateFile(templatePath)
                .set("x", "foo")
                .set("y", "bar")
                .getFileWriterTo(toPath)
                .converge(taskContext);

        assertTrue(converged);

        String actualContent = new UnixPath(toPath).readUtf8File();
        assertEquals("a foo, bar b", actualContent);
    }
}