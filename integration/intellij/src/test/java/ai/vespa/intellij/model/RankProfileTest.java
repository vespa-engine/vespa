// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.model;

import ai.vespa.intellij.schema.model.RankProfile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Test;

import java.io.File;

/**
 * @author bratseth
 */
public class RankProfileTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        // Store the descriptor between tests to make this faster
        return new TestProjectDescriptor();
    }

    @Override
    protected String getTestDataPath() { return "."; }

    @Test
    public void testFindDefinition() {
        super.myFixture.copyDirectoryToProject("src/test/applications/schemainheritance", "/");
        RankProfile profile = RankProfile.fromProjectFile(getProject(), "child.sd", "child_profile");
        assertEquals("child_profile", profile.definition().getName());
    }

}
