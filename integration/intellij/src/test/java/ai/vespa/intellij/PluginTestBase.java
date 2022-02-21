// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Test;

/**
 * Parent of unit tests. This is an antipattern, but mandated by IntelliJ.
 *
 * NOTE: Sometimes, unit tests will stop working with IntelliJ-interna. exceptions. To fix this,
 *       run the first step of "File -> Repair IDE": "Refresh indexable files"
 *
 * @author bratseth
 */
public class PluginTestBase extends LightJavaCodeInsightFixtureTestCase {

    private final TestProjectDescriptor descriptor;

    public PluginTestBase() {
        descriptor = new TestProjectDescriptor(); // Expensive instance
    }

    @Override
    protected LightProjectDescriptor getProjectDescriptor() { return descriptor; }

    @Override
    protected String getTestDataPath() { return "."; }

    protected void useDir(String dir) {
        myFixture.copyDirectoryToProject(dir, "/");
    }

    /** Avoid "no tests" warning */
    @Test
    public void testDummy() {}

    static class TestProjectDescriptor extends LightProjectDescriptor {

    }

}
