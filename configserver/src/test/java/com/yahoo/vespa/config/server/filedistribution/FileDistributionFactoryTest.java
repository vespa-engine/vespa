// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.path.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class FileDistributionFactoryTest {
    private static final ApplicationId application = ApplicationId.from("tenant", "app", "default");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File source;
    private FileDirectory store;

    @Before
    public void setUp() throws IOException {
        source = temporaryFolder.newFolder("source");
        Files.writeString(new File(source, "foo").toPath(), "foo");
        store = new FileDirectory(temporaryFolder.newFolder("store"));
    }

    @Test
    public void newReferencesUseSha256() {
        try (var factory = factory()) {
            FileRegistry registry = factory.createFileRegistry(source, application);
            assertEquals("6bc0de8175d0cf33c2d4cca86c64b0d954b41b44096954b90fd149ce5ce9179f", registry.addFile("foo").value());
            assertEquals(64, registry.addBlob("blob", ByteBuffer.wrap(new byte[] { 1, 2 })).value().length());
        }
    }

    @Test
    public void savedReferencesDoNotResolveIdentity() {
        try (var factory = factory()) {
            AddFileInterface manager = factory.createFileManager(source, () -> {
                throw new IllegalStateException("No saved application identity");
            });
            String legacy = "ea315b7acac56246";
            String sha256 = "6bc0de8175d0cf33c2d4cca86c64b0d954b41b44096954b90fd149ce5ce9179f";
            FileDBRegistry registry = FileDBRegistry.create(manager, new StringReader(
                    "host\nfoo\t" + legacy + "\nblob\t" + sha256 + "\nhttps://example.test/file\t" + legacy + "\n"));
            assertEquals(legacy, registry.addFile("foo").value());
            assertEquals(sha256, registry.addBlob("blob", ByteBuffer.wrap(new byte[] { 9 })).value());
            assertEquals(legacy, registry.addUri("https://example.test/file").value());
            assertThrows(IllegalStateException.class, () -> registry.addFile("new"));
            assertThrows(IllegalStateException.class, () -> registry.addBlob("new", ByteBuffer.allocate(0)));
            assertThrows(IllegalStateException.class, () -> registry.addUri("https://example.test/new"));
            assertEquals(0, store.getRoot().list().length);
        }
    }

    @Test
    public void identityIsResolvedLazilyAndRetained() throws IOException {
        AtomicReference<ApplicationId> identity = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();
        try (var factory = factory()) {
            AddFileInterface manager = factory.createFileManager(source, () -> {
                reads.incrementAndGet();
                return Optional.ofNullable(identity.get()).orElseThrow(() -> new IllegalStateException("No identity"));
            });
            assertEquals(0, reads.get());
            assertThrows(IllegalStateException.class, () -> manager.addFile(Path.fromString("foo")));
            identity.set(application);
            assertEquals(64, manager.addFile(Path.fromString("foo")).value().length());
            assertEquals(64, manager.addBlob(ByteBuffer.allocate(0), Path.fromString("blob")).value().length());
            assertEquals(2, reads.get()); // One failed lookup, then one successful lookup shared by both resources.
        }
    }

    @Test
    public void twoTenantsShareIdenticalResourcesWithoutRewritingThem() throws IOException {
        ApplicationId other = ApplicationId.from("other", "app", "default");
        try (var factory = factory()) {
            FileReference reference = reference(factory, application);
            var stored = store.getFile(reference).orElseThrow().toPath();
            var modified = Files.getLastModifiedTime(stored);
            assertEquals(reference, reference(factory, other));
            assertEquals(reference, reference(factory, application));
            assertEquals(modified, Files.getLastModifiedTime(stored));
            assertEquals("foo", Files.readString(stored));
            assertEquals(1, store.getRoot().list().length);
        }
    }

    private FileReference reference(FileDistributionFactory factory, ApplicationId id) {
        return factory.createFileRegistry(source, id).addFile("foo");
    }

    private FileDistributionFactory factory() {
        return new FileDistributionFactory(new ConfigserverConfig.Builder().hostedVespa(true).build(), store);
    }
}
