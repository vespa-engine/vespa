// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory.HashScheme;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FileDirectorySha256Test {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private FileDirectory fileDirectory;

    @Before
    public void setUp() {
        fileDirectory = new FileDirectory(temporaryFolder.getRoot());
    }

    @Test
    public void identicalContentWithDifferentNamesDoesNotReplaceStoredFiles() throws IOException {
        File json = temporaryFolder.newFile("constant.json");
        File text = temporaryFolder.newFile("constant.txt");
        IOUtils.writeFile(json, "same content", false);
        IOUtils.writeFile(text, "same content", false);

        FileReference jsonReference = fileDirectory.addFile(json, HashScheme.SHA256);
        File storedJson = fileDirectory.getFile(jsonReference).orElseThrow();
        FileReference textReference = fileDirectory.addFile(text, HashScheme.SHA256);

        assertNotEquals(jsonReference, textReference);
        assertTrue(storedJson.exists());
        assertEquals("constant.json", fileDirectory.getFile(jsonReference).orElseThrow().getName());
        assertEquals("constant.txt", fileDirectory.getFile(textReference).orElseThrow().getName());
        assertEquals(jsonReference, fileDirectory.addFile(json, HashScheme.SHA256));
        assertTrue(fileDirectory.getFile(textReference).orElseThrow().exists());
    }

    @Test
    public void swappingContentsBetweenNamesChangesDirectoryReference() throws IOException {
        File first = temporaryFolder.newFolder("first", "tree");
        File second = temporaryFolder.newFolder("second", "tree");
        createFileInSubDir(first, "left", "alpha");
        createFileInSubDir(first, "right", "beta");
        createFileInSubDir(second, "left", "beta");
        createFileInSubDir(second, "right", "alpha");

        FileReference firstReference = fileDirectory.addFile(first, HashScheme.SHA256);
        FileReference secondReference = fileDirectory.addFile(second, HashScheme.SHA256);

        assertNotEquals(firstReference, secondReference);
        assertEquals("alpha", Files.readString(fileDirectory.getFile(firstReference).orElseThrow().toPath().resolve("left")));
        assertEquals("beta", Files.readString(fileDirectory.getFile(secondReference).orElseThrow().toPath().resolve("left")));
    }

    @Test
    public void parentChildRelationshipsAffectDirectoryReference() throws IOException {
        File first = temporaryFolder.newFolder("first", "tree");
        File second = temporaryFolder.newFolder("second", "tree");
        createFileInSubDir(new File(first, "foo/bar"), "value", "same content");
        createFileInSubDir(new File(second, "bar/foo"), "value", "same content");

        FileReference firstReference = fileDirectory.addFile(first, HashScheme.SHA256);
        FileReference secondReference = fileDirectory.addFile(second, HashScheme.SHA256);

        assertNotEquals(firstReference, secondReference);
        assertTrue(new File(fileDirectory.getFile(firstReference).orElseThrow(), "foo/bar/value").isFile());
        assertTrue(new File(fileDirectory.getFile(secondReference).orElseThrow(), "bar/foo/value").isFile());
    }

    @Test
    public void directoryReferenceIsIndependentOfLocationAndCreationOrder() throws IOException {
        File first = temporaryFolder.newFolder("first", "tree");
        File second = temporaryFolder.newFolder("second", "tree");
        createFileInSubDir(first, "left", "alpha");
        createFileInSubDir(first, "right", "beta");
        createFileInSubDir(second, "right", "beta");
        createFileInSubDir(second, "left", "alpha");

        assertEquals(fileDirectory.addFile(first, HashScheme.SHA256), fileDirectory.addFile(second, HashScheme.SHA256));
    }

    @Test
    public void rootDirectoryNameAffectsReference() throws IOException {
        File first = temporaryFolder.newFolder("first");
        File second = temporaryFolder.newFolder("second");
        createFileInSubDir(first, "value", "same content");
        createFileInSubDir(second, "value", "same content");

        FileReference firstReference = fileDirectory.addFile(first, HashScheme.SHA256);
        FileReference secondReference = fileDirectory.addFile(second, HashScheme.SHA256);

        assertNotEquals(firstReference, secondReference);
        assertEquals("first", fileDirectory.getFile(firstReference).orElseThrow().getName());
        assertEquals("second", fileDirectory.getFile(secondReference).orElseThrow().getName());
    }

    @Test
    public void emptyFileAndDirectoryWithTheSameNameHaveDifferentReferences() throws IOException {
        File emptyFile = new File(temporaryFolder.newFolder("first"), "empty");
        Files.createFile(emptyFile.toPath());
        File emptyDirectory = temporaryFolder.newFolder("second", "empty");

        FileReference fileReference = fileDirectory.addFile(emptyFile, HashScheme.SHA256);
        FileReference directoryReference = fileDirectory.addFile(emptyDirectory, HashScheme.SHA256);

        assertNotEquals(fileReference, directoryReference);
        assertTrue(fileDirectory.getFile(fileReference).orElseThrow().isFile());
        assertTrue(fileDirectory.getFile(directoryReference).orElseThrow().isDirectory());
    }

    @Test
    public void fileNameAndContentBoundariesAffectReference() throws IOException {
        File first = temporaryFolder.newFile("ab");
        File second = temporaryFolder.newFile("a");
        IOUtils.writeFile(first, "c", false);
        IOUtils.writeFile(second, "bc", false);

        assertNotEquals(fileDirectory.addFile(first, HashScheme.SHA256), fileDirectory.addFile(second, HashScheme.SHA256));
    }

    @Test
    public void unreadableDirectoryEntryFailsWithoutStoringAReference() throws IOException {
        FileDirectory store = new FileDirectory(temporaryFolder.newFolder("store"));
        File source = temporaryFolder.newFolder("source");
        Files.createSymbolicLink(new File(source, "missing").toPath(), new File(source, "absent").toPath());

        assertThrows(IOException.class, () -> store.addFile(source, HashScheme.SHA256));
        assertEquals(0, store.getRoot().list().length);
    }

    @Test
    public void reloadedRegistryRejectsUnreadableDirectoryEntries() throws IOException {
        FileDirectory store = new FileDirectory(temporaryFolder.newFolder("store"));
        File source = temporaryFolder.newFolder("source");
        File missing = new File(source, "missing");
        Files.createSymbolicLink(missing.toPath(), new File(source, "absent").toPath());
        FileDBRegistry registry = reloadedRegistry(store);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> registry.addFile("source"));
        assertEquals(IOException.class, failure.getCause().getClass());
        assertTrue(failure.getCause().getCause() instanceof FileNotFoundException);
        assertTrue(registry.export().isEmpty());
        assertEquals(0, store.getRoot().list().length);

        Files.delete(missing.toPath());
        Files.writeString(missing.toPath(), "repaired");
        FileReference reference = registry.addFile("source");
        assertEquals(64, reference.value().length());
        assertTrue(store.getFile(reference).orElseThrow().isDirectory());
    }

    @Test
    public void reloadedRegistryStillSilencesAMissingRoot() throws IOException {
        FileDirectory store = new FileDirectory(temporaryFolder.newFolder("store"));
        FileDBRegistry registry = reloadedRegistry(store);

        assertEquals(new FileReference("non-existing-file"), registry.addFile("absent"));
        assertTrue(registry.export().isEmpty());
        assertEquals(0, store.getRoot().list().length);
    }

    private FileDBRegistry reloadedRegistry(FileDirectory store) {
        var manager = new ApplicationFileManager(temporaryFolder.getRoot(), store, false, Optional.empty(), HashScheme.SHA256);
        return FileDBRegistry.create(manager, new StringReader("host\n"));
    }

    @Test
    public void excessiveDepthFailsWithoutStoringAPartialTree() throws IOException {
        FileDirectory store = new FileDirectory(temporaryFolder.newFolder("store"));
        File source = temporaryFolder.newFolder("source");
        File directory = source;
        for (int depth = 0; depth < 101; depth++)
            directory = new File(directory, "d");
        Files.createDirectories(directory.toPath());

        assertThrows(IOException.class, () -> store.addFile(source, HashScheme.SHA256));
        assertEquals(0, store.getRoot().list().length);
    }

    @Test
    public void maximumDepthIsAccepted() throws IOException {
        File source = temporaryFolder.newFolder("source");
        File directory = source;
        for (int depth = 0; depth < 100; depth++) directory = new File(directory, "d");
        Files.createDirectories(directory.toPath());
        FileReference reference = fileDirectory.addFile(source, HashScheme.SHA256);
        assertEquals(64, reference.value().length());
        assertTrue(fileDirectory.getFile(reference).orElseThrow().isDirectory());
    }

    @Test
    public void existingLegacyReferencesRemainReadable() throws IOException {
        FileReference reference = new FileReference("ea315b7acac56246");
        File directory = temporaryFolder.newFolder(reference.value());
        createFileInSubDir(directory, "foo", "foo");

        assertEquals("foo", Files.readString(fileDirectory.getFile(reference).orElseThrow().toPath()));
    }

    @Test
    public void listingFailureDoesNotStoreAReference() throws IOException {
        FileDirectory store = new FileDirectory(temporaryFolder.newFolder("store"));
        File source = new File(temporaryFolder.newFolder("source").getPath()) {
            @Override public File[] listFiles() { return null; }
        };
        assertThrows(IOException.class, () -> store.addFile(source, HashScheme.SHA256));
        assertEquals(0, store.getRoot().list().length);
    }

    @Test
    public void reversedEnumerationGivesTheSameReference() throws IOException {
        File source = temporaryFolder.newFolder("source");
        createFileInSubDir(source, "a", "first");
        createFileInSubDir(source, "b", "second");
        File reversed = new File(source.getPath()) {
            @Override public File[] listFiles() {
                File[] children = super.listFiles();
                Arrays.sort(children, Comparator.comparing(File::getName).reversed());
                return children;
            }
        };
        assertEquals(fileDirectory.addFile(source, HashScheme.SHA256), fileDirectory.addFile(reversed, HashScheme.SHA256));
    }

    @Test
    public void referenceFormatDeterminesTheVerificationScheme() {
        assertEquals(HashScheme.LEGACY, HashScheme.fromReference(new FileReference("0")));
        assertEquals(HashScheme.LEGACY, HashScheme.fromReference(new FileReference("12f292a25163dd9")));
        assertEquals(HashScheme.LEGACY, HashScheme.fromReference(new FileReference("ea315b7acac56246")));
        assertEquals(HashScheme.SHA256, HashScheme.fromReference(new FileReference("0".repeat(64))));
        for (String invalid : new String[] { "", "0".repeat(17), "0".repeat(63), "0".repeat(65), "g", "../foo" })
            assertThrows(IllegalArgumentException.class, () -> HashScheme.fromReference(new FileReference(invalid)));
    }

    @Test
    public void utf8NameLengthAndBinaryContentHaveStableFraming() throws IOException {
        File source = temporaryFolder.newFile("\u00e6");
        Files.write(source.toPath(), new byte[] { 0, 1, (byte) 255 });
        // Independently encoded frame: 66 00000002 c3a6 0000000000000003 0001ff.
        assertEquals("e7375f310fafcd13aa03f277e08bfc204ffac3bf43aea7041bb566b13adbde83",
                     fileDirectory.addFile(source, HashScheme.SHA256).value());
        File emptyDirectory = temporaryFolder.newFolder("foo");
        // Independently encoded frame: 64 00000003 666f6f 00000000.
        assertEquals("b163725ce44e17633bf7b4b845d7825ea411d14e127839c5a4c1b4e87c6be663",
                     fileDirectory.addFile(emptyDirectory, HashScheme.SHA256).value());
    }

    private void createFileInSubDir(File directory, String name, String content) throws IOException {
        Files.createDirectories(directory.toPath());
        IOUtils.writeFile(new File(directory, name), content, false);
    }
}
