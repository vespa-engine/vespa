// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.model;

import ai.vespa.intellij.schema.model.RankProfile;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.utils.Path;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Test;

import java.util.List;

/**
 * @author bratseth
 */
public class SchemaTest extends LightJavaCodeInsightFixtureTestCase {

    private final TestProjectDescriptor descriptor;

    public SchemaTest() {
        descriptor = new TestProjectDescriptor(); // Expensive instance
    }

    @Override
    protected LightProjectDescriptor getProjectDescriptor() { return descriptor; }

    @Override
    protected String getTestDataPath() { return "."; }

    @Test
    public void testSimple() {
        super.myFixture.copyDirectoryToProject("src/test/applications/simple", "/");
        Schema schema = Schema.fromProjectFile(getProject(), Path.fromString("simple.sd"));
        assertNotNull(schema);
        assertEquals("simple", schema.name());
        RankProfile profile = schema.rankProfile("simple-profile").get();
        assertEquals("simple-profile", profile.name());
        List<RankProfile> parents = profile.findInherited();
        assertEquals(2, parents.size());
        assertEquals("parent-profile1", parents.get(0).name());
        assertEquals("parent-profile2", parents.get(1).name());
    }

    @Test
    public void testSchemaInheritance() {
        super.myFixture.copyDirectoryToProject("src/test/applications/schemainheritance", "/");
        Schema schema = Schema.fromProjectFile(getProject(), Path.fromString("child.sd"));
        assertNotNull(schema);
        assertEquals("child", schema.name());
        RankProfile profile = schema.rankProfile("child_profile").get();
        assertEquals("child_profile", profile.name());
        List<RankProfile> parents = profile.findInherited();
        assertEquals(2, parents.size());
        assertEquals("other_child_profile", parents.get(0).name());
        assertEquals("parent-profile", parents.get(1).name());
    }

    @Test
    public void testRankProfileModularity() {
        super.myFixture.copyDirectoryToProject("src/test/applications/rankprofilemodularity", "/");
        Schema schema = Schema.fromProjectFile(getProject(), Path.fromString("test.sd"));
        assertNotNull(schema);
        assertEquals("test", schema.name());
        RankProfile profile = schema.rankProfile("in_schema3").get();
        assertEquals("in_schema3", profile.name());
        List<RankProfile> parents = profile.findInherited();
        assertEquals(2, parents.size());
        assertEquals("outside_schema1", parents.get(0).name());
        assertEquals("outside_schema2", parents.get(1).name());
    }

}
