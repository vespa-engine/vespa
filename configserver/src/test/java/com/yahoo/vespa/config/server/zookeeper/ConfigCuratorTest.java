// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.text.Utf8;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;

/**
 * Tests the ZKFacade using a curator mock.
 *
 * @author hmusum
 */
public class ConfigCuratorTest {

    private final String defKey1 = "attributes";

    private final String payload1 = "attribute[5]\n" +
            "attribute[0].name Popularity\n" +
            "attribute[0].datatype string\n" +
            "attribute[0].collectiontype single\n" +
            "attribute[0].removeifzero false\n" +
            "attribute[0].createifnonexistent false\n" +
            "attribute[0].loadtype \"always\"\n" +
            "attribute[0].uniqueonly false\n" +
            "attribute[0].sparse false\n" +
            "attribute[0].noupdate false\n" +
            "attribute[0].fastsearch false\n" +
            "attribute[0].fastaggregate false\n" +
            "attribute[0].fastersearch false\n" +
            "attribute[1].name atA\n" +
            "attribute[1].datatype string\n" +
            "attribute[1].collectiontype weightedset\n" +
            "attribute[1].removeifzero false\n" +
            "attribute[1].createifnonexistent false\n" +
            "attribute[1].loadtype \"always\"\n" +
            "attribute[1].uniqueonly false\n" +
            "attribute[1].sparse false\n" +
            "attribute[1].noupdate false\n" +
            "attribute[1].fastsearch true\n" +
            "attribute[1].fastaggregate false\n" +
            "attribute[1].fastersearch false\n" +
            "attribute[2].name default_fieldlength\n" +
            "attribute[2].datatype uint32\n" +
            "attribute[2].collectiontype single\n" +
            "attribute[2].removeifzero false\n" +
            "attribute[2].createifnonexistent false\n" +
            "attribute[2].loadtype \"always\"\n" +
            "attribute[2].uniqueonly false\n" +
            "attribute[2].sparse false\n" +
            "attribute[2].noupdate true\n" +
            "attribute[2].fastsearch false\n" +
            "attribute[2].fastaggregate false\n" +
            "attribute[2].fastersearch false\n" +
            "attribute[3].name default_literal_fieldlength\n" +
            "attribute[3].datatype uint32\n" +
            "attribute[3].collectiontype single\n" +
            "attribute[3].removeifzero false\n" +
            "attribute[3].createifnonexistent false\n" +
            "attribute[3].loadtype \"always\"\n" +
            "attribute[3].uniqueonly false\n" +
            "attribute[3].sparse false\n" +
            "attribute[3].noupdate true\n" +
            "attribute[3].fastsearch false\n" +
            "attribute[3].fastaggregate false\n" +
            "attribute[3].fastersearch false\n" +
            "attribute[4].name artist_fieldlength\n" +
            "attribute[4].datatype uint32\n" +
            "attribute[4].collectiontype single\n" +
            "attribute[4].removeifzero false\n" +
            "attribute[4].createifnonexistent false\n" +
            "attribute[4].loadtype \"always\"\n" +
            "attribute[4].uniqueonly false\n" +
            "attribute[4].sparse false\n" +
            "attribute[4].noupdate true\n" +
            "attribute[4].fastsearch false\n" +
            "attribute[4].fastaggregate false\n" +
            "attribute[4].fastersearch false\n";

    private final String payload3 = "attribute[5]\n" +
            "attribute[0].name Popularity\n" +
            "attribute[0].datatype String\n" +
            "attribute[0].collectiontype single\n" +
            "attribute[0].removeifzero false\n" +
            "attribute[0].createifnonexistent false\n" +
            "attribute[0].loadtype \"always\"\n" +
            "attribute[0].uniqueonly false\n" +
            "attribute[0].sparse false\n" +
            "attribute[0].noupdate false\n" +
            "attribute[0].fastsearch false\n" +
            "attribute[0].fastaggregate false\n" +
            "attribute[0].fastersearch false\n" +
            "attribute[1].name atA\n" +
            "attribute[1].datatype string\n" +
            "attribute[1].collectiontype weightedset\n" +
            "attribute[1].removeifzero false\n" +
            "attribute[1].createifnonexistent false\n" +
            "attribute[1].loadtype \"always\"\n" +
            "attribute[1].uniqueonly false\n" +
            "attribute[1].sparse false\n" +
            "attribute[1].noupdate false\n" +
            "attribute[1].fastsearch true\n" +
            "attribute[1].fastaggregate false\n" +
            "attribute[1].fastersearch false\n" +
            "attribute[2].name default_fieldlength\n" +
            "attribute[2].datatype uint32\n" +
            "attribute[2].collectiontype single\n" +
            "attribute[2].removeifzero false\n" +
            "attribute[2].createifnonexistent false\n" +
            "attribute[2].loadtype \"always\"\n" +
            "attribute[2].uniqueonly false\n" +
            "attribute[2].sparse false\n" +
            "attribute[2].noupdate true\n" +
            "attribute[2].fastsearch false\n" +
            "attribute[2].fastaggregate false\n" +
            "attribute[2].fastersearch false\n" +
            "attribute[3].name default_literal_fieldlength\n" +
            "attribute[3].datatype uint32\n" +
            "attribute[3].collectiontype single\n" +
            "attribute[3].removeifzero false\n" +
            "attribute[3].createifnonexistent false\n" +
            "attribute[3].loadtype \"always\"\n" +
            "attribute[3].uniqueonly false\n" +
            "attribute[3].sparse false\n" +
            "attribute[3].noupdate true\n" +
            "attribute[3].fastsearch false\n" +
            "attribute[3].fastaggregate false\n" +
            "attribute[3].fastersearch false\n" +
            "attribute[4].name artist_fieldlength\n" +
            "attribute[4].datatype uint32\n" +
            "attribute[4].collectiontype single\n" +
            "attribute[4].removeifzero false\n" +
            "attribute[4].createifnonexistent false\n" +
            "attribute[4].loadtype \"always\"\n" +
            "attribute[4].uniqueonly false\n" +
            "attribute[4].sparse false\n" +
            "attribute[4].noupdate true\n" +
            "attribute[4].fastsearch false\n" +
            "attribute[4].fastaggregate false\n" +
            "attribute[4].fastersearch false\n";

    private void initAndClearZK(ConfigCurator zkIf) {
        zkIf.initAndClear(ConfigCurator.DEFCONFIGS_ZK_SUBPATH);
        zkIf.initAndClear(ConfigCurator.USERAPP_ZK_SUBPATH);
    }

    private ConfigCurator deployApp() {
        ConfigCurator zkIf = create();
        initAndClearZK(zkIf);
        zkIf.putData(ConfigCurator.DEFCONFIGS_ZK_SUBPATH, defKey1, payload1);
        // zkIf.putData(ConfigCurator.USERCONFIGS_ZK_SUBPATH, cfgKey1, payload3);
        String partitionsDef = "version=7\\n" +
                "dataset[].id       int\\n" +
                "dataset[].partbits                      int default=6";
        zkIf.putData(ConfigCurator.DEFCONFIGS_ZK_SUBPATH, "partitions", partitionsDef);
        String partitionsUser = "dataset[0].partbits 8\\n";
        // zkIf.putData(ConfigCurator.USERCONFIGS_ZK_SUBPATH, "partitions", partitionsUser);
        return zkIf;
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testZKInterface() {
        ConfigCurator zkIf = create();
        zkIf.putData("", "test", "foo");
        zkIf.putData("/test", "me", "bar");
        zkIf.putData("", "test;me;now,then", "baz");
        assertEquals(zkIf.getData("", "test"), "foo");
        assertEquals(zkIf.getData("/test", "me"), "bar");
        assertEquals(zkIf.getData("", "test;me;now,then"), "baz");
    }

    @Test
    public void testNonExistingPath() {
        ConfigCurator configCurator = create();

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Cannot read data from path /non-existing, it does not exist");
        configCurator.getData("/non-existing");
    }

    @Test
    public void testWatcher() {
        ConfigCurator zkIf = create();

        zkIf.putData("", "test", "foo");
        String data = zkIf.getData("/test");
        assertEquals(data, "foo");
        zkIf.putData("", "/test", "bar");
        data = zkIf.getData("/test");
        assertEquals(data, "bar");

        zkIf.getChildren("/");
        zkIf.putData("", "test2", "foo2");
    }

    private ConfigCurator create() {
        return ConfigCurator.create(new MockCurator());
    }

    @Test
    public void testGetDeployedData() {
        ConfigCurator zkIf = deployApp();
        assertEquals(zkIf.getData(ConfigCurator.DEFCONFIGS_ZK_SUBPATH, defKey1), payload1);
    }

    @Test
    public void testEmptyData() {
        ConfigCurator zkIf = create();
        zkIf.createNode("/empty", "data");
        assertEquals("", zkIf.getData("/empty", "data"));
    }

    @Test
    public void testRecursiveDelete() {
        ConfigCurator configCurator = create();
        configCurator.putData("/foo", Utf8.toBytes("sadsdfsdfsdfsdf"));
        configCurator.putData("/foo/bar", Utf8.toBytes("dsfsdffds"));
        configCurator.putData("/foo/baz",
                Utf8.toBytes("sdf\u00F8l ksdfl skdflsk dflsdkfd welkr3k lkr e4kt4 54l4l353k l534klk3lk4l33k5l 353l4k l43k l4k"));
        configCurator.putData("/foo/bar/dill", Utf8.toBytes("sdfsfe 23 42 3 3 2342"));
        configCurator.putData("/foo", Utf8.toBytes("sdcfsdfsdf"));
        configCurator.putData("/foo", Utf8.toBytes("sdcfsd sdfdffsdf"));
        configCurator.deleteRecurse("/foo");
        assertFalse(configCurator.exists("/foo"));
        assertFalse(configCurator.exists("/foo/bar"));
        assertFalse(configCurator.exists("/foo/bar/dill"));
        assertFalse(configCurator.exists("/foo/bar/baz"));
        try {
            configCurator.getChildren("/foo");
            fail("Got children from nonexisting ZK path");
        } catch (RuntimeException e) {
            assertTrue(e.getCause().getMessage().matches(".*NoNode.*"));
        }
        configCurator.deleteRecurse("/nonexisting");
    }

}
