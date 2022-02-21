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
        assertEquals(2, profile.parents().size());
        assertEquals("parent-profile1", profile.parents().get("parent-profile1").name());
        assertEquals("parent-profile2", profile.parents().get("parent-profile2").name());
        assertEquals(0, schema.functions().size());
    }

    @Test
    public void testSchemaInheritance() {
        useDir("src/test/applications/schemaInheritance");
        Schema child = Schema.fromProjectFile(getProject(), Path.fromString("child.sd"));
        assertNotNull(child);
        assertEquals("child", child.name());
        assertEquals("parent", child.parent().get().name());
        Schema parent = child.parent().get();
        assertEquals("child", parent.children().get("child").name());

        assertEquals(3, child.rankProfiles().size());
        assertTrue(child.rankProfiles().containsKey("child_profile"));
        assertTrue(child.rankProfiles().containsKey("other_child_profile"));
        assertTrue(child.rankProfiles().containsKey("parent_profile"));

        RankProfile profile = child.rankProfiles().get("child_profile");
        assertEquals("child_profile", profile.name());
        assertEquals(2, profile.parents().size());
        assertEquals("other_child_profile", profile.parents().get("other_child_profile").name());
        assertEquals("parent_profile", profile.parents().get("parent_profile").name());
        assertEquals("child_profile", profile.parents().get("parent_profile").children().get(0).name());
        assertEquals(2, child.functions().size());
    }

    @Test
    public void testRankProfileModularity() {
        useDir("src/test/applications/rankProfileModularity");
        Schema schema = Schema.fromProjectFile(getProject(), Path.fromString("test.sd"));
        assertNotNull(schema);
        assertEquals("test", schema.name());
        RankProfile profile = schema.rankProfiles().get("in_schema3");
        assertEquals("in_schema3", profile.name());
        assertEquals(2, profile.parents().size());
        assertEquals("outside_schema1", profile.parents().get("outside_schema1").name());
        assertEquals("outside_schema2", profile.parents().get("outside_schema2").name());
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
