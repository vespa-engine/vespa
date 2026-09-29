// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.path.Path;
import com.yahoo.vespa.flags.Dimension;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.RawFlag;
import com.yahoo.vespa.flags.json.FlagData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class FileDistributionFactoryTest {
    private static final ApplicationId application = ApplicationId.from("tenant", "app", "default");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final InMemoryFlagSource flags = new InMemoryFlagSource();
    private File source;
    private FileDirectory store;

    @Before
    public void setUp() throws IOException {
        source = temporaryFolder.newFolder("source");
        Files.writeString(new File(source, "foo").toPath(), "foo");
        store = new FileDirectory(temporaryFolder.newFolder("store"));
    }

    @Test
    public void defaultAndPercentageEndpoints() {
        try (var factory = factory(flags)) {
            assertEquals("ea315b7acac56246", reference(factory, application).value());
            percent(100);
            assertEquals("6bc0de8175d0cf33c2d4cca86c64b0d954b41b44096954b90fd149ce5ce9179f", reference(factory, application).value());
            percent(0);
            assertEquals("ea315b7acac56246", reference(factory, application).value());
        }
    }

    @Test
    public void stableFullIdentityBucketBoundaries() {
        try (var factory = factory(flags)) {
            // Fixed expectations pin the serialized full ID, Java hashCode and floorMod mapping.
            checkBoundary(factory, application, 40);
            checkBoundary(factory, ApplicationId.from("tenant", "app", "canary"), 17);
            checkBoundary(factory, ApplicationId.from("other", "app", "default"), 30);
        }
    }

    @Test
    public void instanceOverrideUsesAllApplicationDimensions() {
        FlagData data = FlagData.deserialize("""
                {"id":"application-update-rollout-percent","rules":[
                  {"conditions":[{"type":"whitelist","dimension":"instance","values":["tenant:app:default"]}],"value":100},
                  {"value":0}
                ]}
                """);
        FlagSource source = flagSource((id, vector) -> {
            assertEquals(Flags.APPLICATION_UPDATE_ROLLOUT_PERCENT.id(), id);
            assertEquals(Optional.of("tenant"), vector.getValue(Dimension.TENANT_ID));
            assertEquals(Optional.of("tenant:app"), vector.getValue(Dimension.APPLICATION));
            return data.resolve(vector);
        });
        try (var factory = factory(source)) {
            assertEquals(64, reference(factory, application).value().length());
            assertEquals("ea315b7acac56246", reference(factory, ApplicationId.from("tenant", "app", "canary")).value());
        }
    }

    @Test
    public void registryResolvesTheFlagOnceBeforeAddingResources() throws IOException {
        AtomicInteger reads = new AtomicInteger();
        percent(100);
        try (var factory = factory((id, vector) -> {
            reads.incrementAndGet();
            return flags.fetch(id, vector);
        })) {
            FileRegistry registry = factory.createFileRegistry(source, application);
            assertEquals(1, reads.get());
            percent(0);
            assertEquals(64, registry.addFile("foo").value().length());
            assertEquals(64, registry.addBlob("blob", ByteBuffer.wrap(new byte[] { 1, 2 })).value().length());
            assertEquals(1, reads.get());
            assertEquals("ea315b7acac56246", reference(factory, application).value());
            assertEquals(2, reads.get());
        }
    }

    @Test
    public void savedReferencesNeedNeitherIdentityNorFlagEvaluation() throws IOException {
        AtomicInteger reads = new AtomicInteger();
        try (var factory = factory((id, vector) -> {
            reads.incrementAndGet();
            throw new AssertionError("Saved references must not evaluate flags");
        })) {
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
            assertEquals(0, reads.get());
            assertEquals(0, store.getRoot().list().length);
        }
    }

    @Test
    public void identityCanAppearAfterManagerConstructionAndSchemeIsThenRetained() throws IOException {
        AtomicReference<ApplicationId> identity = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();
        try (var factory = factory((id, vector) -> {
            reads.incrementAndGet();
            return flags.fetch(id, vector);
        })) {
            AddFileInterface manager = factory.createFileManager(source, () -> Optional.ofNullable(identity.get()).orElseThrow(() -> new IllegalStateException("No identity")));
            assertEquals(0, reads.get());
            assertThrows(IllegalStateException.class, () -> manager.addFile(Path.fromString("foo")));
            identity.set(application);
            percent(100);
            assertEquals(64, manager.addFile(Path.fromString("foo")).value().length());
            percent(0);
            assertEquals(64, manager.addBlob(ByteBuffer.allocate(0), Path.fromString("blob")).value().length());
            assertEquals(1, reads.get());
        }
    }

    @Test
    public void twoTenantsShareIdenticalResourcesAndMigrationIsStable() throws IOException {
        ApplicationId other = ApplicationId.from("other", "app", "default");
        try (var factory = factory(flags)) {
            FileReference legacy = reference(factory, application);
            assertEquals(legacy, reference(factory, other));
            percent(100);
            FileReference migrated = reference(factory, application);
            assertNotEquals(legacy, migrated);
            assertEquals(migrated, reference(factory, other));
            var stored = store.getFile(migrated).orElseThrow().toPath();
            var modified = Files.getLastModifiedTime(stored);
            assertEquals(migrated, reference(factory, application));
            assertEquals(modified, Files.getLastModifiedTime(stored));
            assertEquals("foo", Files.readString(store.getFile(legacy).orElseThrow().toPath()));
            assertEquals("foo", Files.readString(stored));
            assertEquals(2, store.getRoot().list().length);
        }
    }

    @Test
    public void invalidPercentagesFailExplicitly() {
        try (var factory = factory(flags)) {
            for (int percent : List.of(-1, 101, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                percent(percent);
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> factory.createFileRegistry(source, application));
                assertEquals("application-update-rollout-percent must be between 0 and 100: " + percent, exception.getMessage());
            }
            assertEquals(0, store.getRoot().list().length);
        }
    }

    private void checkBoundary(FileDistributionFactory factory, ApplicationId id, int bucket) {
        percent(bucket);
        assertEquals("ea315b7acac56246", reference(factory, id).value());
        percent(bucket + 1);
        assertEquals(64, reference(factory, id).value().length());
    }

    private FileReference reference(FileDistributionFactory factory, ApplicationId id) {
        return factory.createFileRegistry(source, id).addFile("foo");
    }

    private void percent(int value) {
        flags.withIntFlag(Flags.APPLICATION_UPDATE_ROLLOUT_PERCENT.id(), value);
    }

    private FileDistributionFactory factory(BiFunction<FlagId, FetchVector, Optional<RawFlag>> fetch) {
        return factory(flagSource(fetch));
    }

    private FlagSource flagSource(BiFunction<FlagId, FetchVector, Optional<RawFlag>> fetch) {
        return new InMemoryFlagSource() {
            @Override public Optional<RawFlag> fetch(FlagId id, FetchVector vector) {
                return fetch.apply(id, vector);
            }
        };
    }

    private FileDistributionFactory factory(FlagSource source) {
        return new FileDistributionFactory(new ConfigserverConfig.Builder().hostedVespa(true).build(), store, source);
    }
}
