// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.lang.MutableBoolean;
import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hakonhall
 */
class TemplateFileSyncTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final UnixPath destinationPath = new UnixPath(fileSystem.getPath("/dir/filename.txt"));
    private final TemplateFileSync<Inputs> templateFileSync = new TemplateFileSync<>(makeTemplateFile(fileSystem),
                                                                                     new TemplateDescriptor(),
                                                                                     destinationPath.toPath(),
                                                                                     Inputs::requireRender);
    private final TestTaskContext context = new TestTaskContext();
    private final MutableBoolean onceInvoked = new MutableBoolean(false);
    private final MutableBoolean fillInvoked = new MutableBoolean(false);

    private Inputs inputs = new Inputs();

    @Test
    void verifyBasics() {
        // Initial converge creates directory, file and content
        assertTrue(convergeWithInput(0));
        assertTrue(onceInvoked.get());
        assertTrue(fillInvoked.get());
        assertFileContent("base 0");

        // Converging again is a no-op
        assertFalse(convergeWithInput(0));
        assertFalse(onceInvoked.get());
        assertFalse(fillInvoked.get());
        assertFileContent("base 0");

        // Changing inputs updates content
        assertTrue(convergeWithInput(1));
        assertFalse(onceInvoked.get());
        assertTrue(fillInvoked.get());
        assertFileContent("base 1");

        // next is then a no-op
        assertFalse(convergeWithInput(1));
        assertFalse(onceInvoked.get());
        assertFalse(fillInvoked.get());
        assertFileContent("base 1");

        // Removing file makes it write the file again
        destinationPath.deleteIfExists();
        assertTrue(convergeWithInput(1));
        assertFalse(onceInvoked.get());
        assertFalse(fillInvoked.get()); // last render was cached
        assertFileContent("base 1");

        // next is then a no-op
        assertFalse(convergeWithInput(1));
        assertFalse(onceInvoked.get());
        assertFalse(fillInvoked.get());
        assertFileContent("base 1");
    }

    private boolean convergeWithInput(int i) {
        onceInvoked.set(false);
        fillInvoked.set(false);
        inputs = new Inputs();
        inputs.i = i;

        return templateFileSync.converge(context,
                                     baseTemplate -> {
                                         onceInvoked.set(true);
                                         baseTemplate.set("first", "base");
                                     },
                                         inputs,
                                         (template, inputs_) -> {
                                         fillInvoked.set(true);
                                         Inputs.fill(template, inputs_);
                                     });
    }

    private void assertFileContent(String content) {
        String actualContent = destinationPath.readUtf8File();
        assertEquals(content, actualContent);
    }

    private static Path makeTemplateFile(FileSystem fileSystem) {
        return new UnixPath(fileSystem.getPath("/tmp/template.tmp"))
                .createParents()
                .writeUtf8File("%{=first} %{=second}")
                .toPath();
    }

    private static class Inputs {
        private int i = 0;

        public static void fill(Template template, Inputs self) {
            template.set("second", self.i);
        }

        public static boolean requireRender(Inputs oldInputs, Inputs newInputs) {
            return oldInputs.i != newInputs.i;
        }
    }
}