// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.model;

import ai.vespa.intellij.PluginTestBase;
import ai.vespa.intellij.schema.model.RankProfile;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.utils.Path;
import org.junit.Test;

/**
 * @author bratseth
 */
public class SchemaTest extends PluginTestBase {

    @Test
    public void testSimple() {
        useDir("src/test/applications/simple");
        Schema schema = Schema.fromProjectFile(getProject(), Path.fromString("simple.sd"));
        assertNotNull(schema);
        assertEquals("simple", schema.name());
        RankProfile profile = schema.rankProfiles().get("simple-profile");
        assertEquals("simple-profile", profile.name());
        assertEquals(2, profile.inherited().size());
        assertEquals("parent-profile1", profile.inherited().get("parent-profile1").name());
        assertEquals("parent-profile2", profile.inherited().get("parent-profile2").name());
        assertEquals(0, schema.functions().size());
    }

    @Test
    public void testSchemaInheritance() {
        useDir("src/test/applications/schemaInheritance");
        Schema schema = Schema.fromProjectFile(getProject(), Path.fromString("child.sd"));
        assertNotNull(schema);
        assertEquals("child", schema.name());
        RankProfile profile = schema.rankProfiles().get("child_profile");
        assertEquals("child_profile", profile.name());
        assertEquals(2, profile.inherited().size());
        assertEquals("other_child_profile", profile.inherited().get("other_child_profile").name());
        assertEquals("parent_profile", profile.inherited().get("parent_profile").name());
        assertEquals(0, schema.functions().size());
    }

    @Test
    public void testRankProfileModularity() {
        useDir("src/test/applications/rankProfileModularity");
        Schema schema = Schema.fromProjectFile(getProject(), Path.fromString("test.sd"));
        assertNotNull(schema);
        assertEquals("test", schema.name());
        RankProfile profile = schema.rankProfiles().get("in_schema3");
        assertEquals("in_schema3", profile.name());
        assertEquals(2, profile.inherited().size());
        assertEquals("outside_schema1", profile.inherited().get("outside_schema1").name());
        assertEquals("outside_schema2", profile.inherited().get("outside_schema2").name());
        schema.functions().entrySet().stream().forEach(e -> System.out.println(e));
        assertEquals("8 proper functions + first-phase", 9, schema.functions().size());
        assertEquals(schema.rankProfiles().get("in_schema2").definedFunctions().get("ff1"),
                     schema.functions().get("ff1"));
        assertEquals(schema.rankProfiles().get("outside_schema1").definedFunctions().get("local1"),
                     schema.functions().get("local1"));
        assertEquals(4, schema.rankProfiles().get("outside_schema1").definedFunctions().size());
        assertEquals(1, schema.rankProfiles().get("in_schema1").children().size());
        assertEquals(4, schema.rankProfiles().get("outside_schema2").children().size());
    }

}
