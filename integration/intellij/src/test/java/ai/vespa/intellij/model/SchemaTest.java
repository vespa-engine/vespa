// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.model;

import ai.vespa.intellij.schema.SdUtil;
import ai.vespa.intellij.schema.model.RankProfile;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
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
        Schema schema = Schema.fromProjectFile(getProject(), "simple.sd");
        assertNotNull(schema);
        assertEquals("simple.sd", schema.definition().getName());
        RankProfile profile = RankProfile.fromProjectFile(getProject(), "simple.sd", "simple-profile");
        assertEquals("simple-profile", profile.definition().getName());
        List<SdRankProfileDefinition> parents = SdUtil.getRankProfileParents(profile.definition());
        assertEquals(2, parents.size());
        assertEquals("parent-profile1", parents.get(0).getName());
        assertEquals("parent-profile2", parents.get(1).getName());
    }

    @Test
    public void testSchemaInheritance() {
        super.myFixture.copyDirectoryToProject("src/test/applications/schemainheritance", "/");
        Schema schema = Schema.fromProjectFile(getProject(), "child.sd");
        assertNotNull(schema);
        assertEquals("child.sd", schema.definition().getName());
        RankProfile profile = RankProfile.fromProjectFile(getProject(), "child.sd", "child_profile");
        assertEquals("child_profile", profile.definition().getName());
        List<SdRankProfileDefinition> parents = SdUtil.getRankProfileParents(profile.definition());
        assertEquals(2, parents.size());
        assertEquals("other_child_profile", parents.get(0).getName());
        // assertEquals("parent-profile", parents.get(1).getName()); TODO
    }

}
