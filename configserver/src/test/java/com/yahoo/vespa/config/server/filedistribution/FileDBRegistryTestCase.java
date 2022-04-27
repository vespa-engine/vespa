// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

/**
 * @author Tony Vaagenes
 */
public class FileDBRegistryTestCase {

    private static final String BLOB = "Some blob";
    private static final String APP = "src/test/apps/zkapp";
    private static final String FOO_FILE = "files/foo.json";
    private static final String NO_FOO_FILE = "files/no_foo.json";
    private static final String BOO_FILE = "/files/no_foo.json";
    private static final String BAR_FILE = "../files/no_foo.json";
    private static final String BLOB_NAME = "././myblob.name";
    private static final FileReference BLOB_REF = new FileReference("12f292a25163dd9");
    private static final FileReference FOO_REF = new FileReference("b5ce94ca1feae86c");

    @Test
    public void uriResourcesNotSupportedWhenHosted() {
        assertEquals("URI type resources are not supported in this Vespa cloud",
                     assertThrows(IllegalArgumentException.class,
                                  () -> new ApplicationFileManager(null, null, true).addUri(null, null))
                             .getMessage());
    }

    @Test
    public void importAndExport() throws IOException {
        TemporaryFolder tmpDir = new TemporaryFolder();
        tmpDir.create();
        AddFileInterface fileManager = new ApplicationFileManager(new File(APP), new FileDirectory(tmpDir.newFolder()), false);
        FileRegistry fileRegistry = new FileDBRegistry(fileManager);
        assertEquals(FOO_REF, fileRegistry.addFile(FOO_FILE));
        try {
            fileRegistry.addFile(NO_FOO_FILE);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("src/test/apps/zkapp/files/no_foo.json (No such file or directory)", e.getCause().getMessage());
        }
        try {
            fileRegistry.addFile(BOO_FILE);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("/files/no_foo.json is not relative", e.getMessage());
        }
        try {
            fileRegistry.addFile(BAR_FILE);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("'..' is not allowed in path", e.getMessage());
        }
        assertEquals(BLOB_REF, fileRegistry.addBlob(BLOB_NAME, ByteBuffer.wrap(BLOB.getBytes(StandardCharsets.UTF_8))));
        String serializedRegistry = FileDBRegistry.exportRegistry(fileRegistry);

        FileDBRegistry importedRegistry = FileDBRegistry.create(fileManager, new StringReader(serializedRegistry));

        assertEquals(Set.of(BLOB_NAME, FOO_FILE), importedRegistry.getMap().keySet());
        assertEquals(BLOB_REF, importedRegistry.getMap().get(BLOB_NAME));
        assertEquals(FOO_REF, importedRegistry.getMap().get(FOO_FILE));

        assertEquals(2, importedRegistry.export().size());

        checkConsistentEntry(fileRegistry.export().get(0), importedRegistry);
        checkConsistentEntry(fileRegistry.export().get(1), importedRegistry);

        assertEquals(new FileReference("non-existing-file"), importedRegistry.addFile(NO_FOO_FILE));
        assertEquals(2, importedRegistry.export().size());
        tmpDir.delete();
    }

    void checkConsistentEntry(FileRegistry.Entry entry, FileRegistry registry) {
        assertEquals(entry.reference, registry.addFile(entry.relativePath));
    }
    
}
