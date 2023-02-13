// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils;

import com.yahoo.config.FileNode;
import com.yahoo.config.FileReference;
import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;
import com.yahoo.vespa.model.SimpleConfigProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Ulf Lilleengen
 */
public class FileSenderTest {

    private SimpleConfigProducer<?> producer;
    private ConfigPayloadBuilder builder;
    private List<AbstractService> serviceList;
    private final MyFileRegistry fileRegistry = new MyFileRegistry();
    private ConfigDefinition def;
    private TestService service;

    private static class MyFileRegistry implements FileRegistry {
        public Map<String, FileReference> pathToRef = new HashMap<>();
        @Override
        public FileReference addFile(String relativePath) {
            return pathToRef.get(relativePath);
        }

        @Override
        public FileReference addUri(String uri) {
            return null;
        }

        @Override
        public FileReference addBlob(String name, ByteBuffer blob) {
            return null;
        }

        @Override
        public List<Entry> export() {
            return null;
        }
    }

    private FileSender fileSender() {
        return new FileSender(serviceList, fileRegistry, new BaseDeployLogger());
    }

    @BeforeEach
    public void setup() {
        MockRoot root = new MockRoot();
        producer = new SimpleConfigProducer<>(root, "test");
        service = new TestService(root, "service");
        serviceList = new ArrayList<>();
        serviceList.add(service);
        ConfigDefinitionKey key = new ConfigDefinitionKey("myname", "mynamespace");
        def = new ConfigDefinition("myname", "mynamespace");
        builder = new ConfigPayloadBuilder(def);
        Map<ConfigDefinitionKey, ConfigPayloadBuilder> builderMap = new HashMap<>();
        builderMap.put(key, builder);
        UserConfigRepo testRepo = new UserConfigRepo(builderMap);
        producer.setUserConfigs(testRepo);
    }

    @Test
    void require_that_simple_file_fields_are_modified() {
        def.addFileDef("fileVal");
        def.addStringDef("stringVal");
        builder.setField("fileVal", "foo.txt");
        builder.setField("stringVal", "foo.txt");
        fileRegistry.pathToRef.put("foo.txt", new FileNode("fooshash").value());
        fileSender().sendUserConfiguredFiles(producer);
        assertEquals("fooshash", builder.getObject("fileVal").getValue());
        assertEquals("foo.txt", builder.getObject("stringVal").getValue());
    }

    @Test
    void require_that_simple_path_fields_are_modified() {
        def.addPathDef("fileVal");
        def.addStringDef("stringVal");
        builder.setField("fileVal", "foo.txt");
        builder.setField("stringVal", "foo.txt");
        fileRegistry.pathToRef.put("foo.txt", new FileNode("fooshash").value());
        fileSender().sendUserConfiguredFiles(producer);
        assertEquals("fooshash", builder.getObject("fileVal").getValue());
        assertEquals("foo.txt", builder.getObject("stringVal").getValue());
    }

    @Test
    void require_that_simple_model_field_with_just_path_is_modified() {
        var originalValue = ModelReference.unresolved(new FileReference("myModel.onnx"));
        def.addModelDef("modelVal");
        builder.setField("modelVal", originalValue.toString());
        assertFileSent("myModel.onnx", originalValue);
    }

    @Test
    void require_that_simple_model_field_with_path_and_url_is_modified() {
        var originalValue = ModelReference.unresolved(Optional.empty(),
                                                      Optional.of(new UrlReference("myUrl")),
                                                      Optional.of(new FileReference("myModel.onnx")));
        def.addModelDef("modelVal");
        builder.setField("modelVal", originalValue.toString());
        assertFileSent("myModel.onnx", originalValue);
    }

    @Test
    void require_that_simple_model_field_with_just_url_is_not_modified() {
        var originalValue = ModelReference.unresolved(new UrlReference("myUrl"));
        def.addModelDef("modelVal");
        builder.setField("modelVal",originalValue.toString());
        fileSender().sendUserConfiguredFiles(producer);
        assertEquals(originalValue, ModelReference.valueOf(builder.getObject("modelVal").getValue()));
    }

    private void assertFileSent(String path, ModelReference originalValue) {
        fileRegistry.pathToRef.put(path, new FileNode("myModelHash").value());
        fileSender().sendUserConfiguredFiles(producer);
        var expected = ModelReference.unresolved(originalValue.modelId(),
                                                 originalValue.url(),
                                                 Optional.of(new FileReference("myModelHash")));
        assertEquals(expected, ModelReference.valueOf(builder.getObject("modelVal").getValue()));
    }

    @Test
    void require_that_fields_in_inner_arrays_are_modified() {
        def.innerArrayDef("inner").addFileDef("fileVal");
        def.innerArrayDef("inner").addStringDef("stringVal");
        ConfigPayloadBuilder inner = builder.getArray("inner").append();
        inner.setField("fileVal", "bar.txt");
        inner.setField("stringVal", "bar.txt");
        fileRegistry.pathToRef.put("bar.txt", new FileNode("barhash").value());
        fileSender().sendUserConfiguredFiles(producer);
        assertEquals("barhash", builder.getArray("inner").get(0).getObject("fileVal").getValue());
        assertEquals("bar.txt", builder.getArray("inner").get(0).getObject("stringVal").getValue());
    }

    @Test
    void require_that_paths_and_model_fields_are_modified() {
        def.arrayDef("fileArray").setTypeSpec(new ConfigDefinition.TypeSpec("fileArray", "file", null, null, null, null));
        def.arrayDef("pathArray").setTypeSpec(new ConfigDefinition.TypeSpec("pathArray", "path", null, null, null, null));
        def.arrayDef("stringArray").setTypeSpec(new ConfigDefinition.TypeSpec("stringArray", "string", null, null, null, null));
        builder.getArray("fileArray").append("foo.txt");
        builder.getArray("fileArray").append("bar.txt");
        builder.getArray("pathArray").append("path.txt");
        builder.getArray("stringArray").append("foo.txt");
        fileRegistry.pathToRef.put("foo.txt", new FileNode("foohash").value());
        fileRegistry.pathToRef.put("bar.txt", new FileNode("barhash").value());
        fileRegistry.pathToRef.put("path.txt", new FileNode("pathhash").value());
        fileSender().sendUserConfiguredFiles(producer);
        assertEquals("foohash", builder.getArray("fileArray").get(0).getValue());
        assertEquals("barhash", builder.getArray("fileArray").get(1).getValue());
        assertEquals("pathhash", builder.getArray("pathArray").get(0).getValue());
        assertEquals("foo.txt", builder.getArray("stringArray").get(0).getValue());
    }

    @Test
    void require_that_arrays_are_modified() {
        def.arrayDef("fileArray").setTypeSpec(new ConfigDefinition.TypeSpec("fileArray", "file", null, null, null, null));
        def.arrayDef("pathArray").setTypeSpec(new ConfigDefinition.TypeSpec("pathArray", "path", null, null, null, null));
        def.arrayDef("stringArray").setTypeSpec(new ConfigDefinition.TypeSpec("stringArray", "string", null, null, null, null));
        builder.getArray("fileArray").append("foo.txt");
        builder.getArray("fileArray").append("bar.txt");
        builder.getArray("pathArray").append("path.txt");
        builder.getArray("stringArray").append("foo.txt");
        fileRegistry.pathToRef.put("foo.txt", new FileNode("foohash").value());
        fileRegistry.pathToRef.put("bar.txt", new FileNode("barhash").value());
        fileRegistry.pathToRef.put("path.txt", new FileNode("pathhash").value());
        fileSender().sendUserConfiguredFiles(producer);
        assertEquals("foohash", builder.getArray("fileArray").get(0).getValue());
        assertEquals("barhash", builder.getArray("fileArray").get(1).getValue());
        assertEquals("pathhash", builder.getArray("pathArray").get(0).getValue());
        assertEquals("foo.txt", builder.getArray("stringArray").get(0).getValue());
    }

    @Test
    void require_that_structs_are_modified() {
        def.structDef("struct").addFileDef("fileVal");
        def.structDef("struct").addStringDef("stringVal");
        builder.getObject("struct").setField("fileVal", "foo.txt");
        builder.getObject("struct").setField("stringVal", "foo.txt");
        fileRegistry.pathToRef.put("foo.txt", new FileNode("foohash").value());
        fileSender().sendUserConfiguredFiles(producer);
        assertEquals("foohash", builder.getObject("struct").getObject("fileVal").getValue());
        assertEquals("foo.txt", builder.getObject("struct").getObject("stringVal").getValue());
    }

    @Test
    void require_that_leaf_maps_are_modified() {
        def.leafMapDef("fileMap").setTypeSpec(new ConfigDefinition.TypeSpec("fileMap", "file", null, null, null, null));
        def.leafMapDef("pathMap").setTypeSpec(new ConfigDefinition.TypeSpec("pathMap", "path", null, null, null, null));
        def.leafMapDef("stringMap").setTypeSpec(new ConfigDefinition.TypeSpec("stringMap", "string", null, null, null, null));
        builder.getMap("fileMap").put("foo", "foo.txt");
        builder.getMap("fileMap").put("bar", "bar.txt");
        builder.getMap("pathMap").put("path", "path.txt");
        builder.getMap("stringMap").put("bar", "bar.txt");
        fileRegistry.pathToRef.put("foo.txt", new FileNode("foohash").value());
        fileRegistry.pathToRef.put("bar.txt", new FileNode("barhash").value());
        fileRegistry.pathToRef.put("path.txt", new FileNode("pathhash").value());
        fileSender().sendUserConfiguredFiles(producer);
        assertEquals("foohash", builder.getMap("fileMap").get("foo").getValue());
        assertEquals("barhash", builder.getMap("fileMap").get("bar").getValue());
        assertEquals("pathhash", builder.getMap("pathMap").get("path").getValue());
        assertEquals("bar.txt", builder.getMap("stringMap").get("bar").getValue());
    }

    @Test
    void require_that_fields_in_inner_maps_are_modified() {
        def.structMapDef("inner").addFileDef("fileVal");
        def.structMapDef("inner").addStringDef("stringVal");
        ConfigPayloadBuilder inner = builder.getMap("inner").put("foo");
        inner.setField("fileVal", "bar.txt");
        inner.setField("stringVal", "bar.txt");
        fileRegistry.pathToRef.put("bar.txt", new FileNode("barhash").value());
        fileSender().sendUserConfiguredFiles(producer);
        assertEquals("barhash", builder.getMap("inner").get("foo").getObject("fileVal").getValue());
        assertEquals("bar.txt", builder.getMap("inner").get("foo").getObject("stringVal").getValue());
    }

    @Test
    void require_that_null_files_are_not_sent() {
        assertThrows(IllegalArgumentException.class, () -> {
            def.addFileDef("fileVal");
            fileRegistry.pathToRef.put("foo.txt", new FileNode("fooshash").value());
            fileSender().sendUserConfiguredFiles(producer);
        });
    }


    private static class TestService extends AbstractService {
        public TestService(TreeConfigProducer<?> parent, String name) {
            super(parent, name);
        }

        @Override
        public int getPortCount() {
            return 0;
        }

        @Override public void allocatePorts(int start, PortAllocBridge from) { }
    }
}
