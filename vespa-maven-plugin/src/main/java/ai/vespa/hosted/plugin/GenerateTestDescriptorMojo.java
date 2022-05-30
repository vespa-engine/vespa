// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import ai.vespa.hosted.api.TestDescriptor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Generates a test descriptor file based on content of the compiled test classes
 *
 * @author bjorncs
 */
@Mojo(name = "generateTestDescriptor", threadSafe = true)
public class GenerateTestDescriptorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        TestAnnotationAnalyzer analyzer = new TestAnnotationAnalyzer();
        analyzeTestClasses(analyzer);
        TestDescriptor descriptor = TestDescriptor.from(
                TestDescriptor.CURRENT_VERSION,
                analyzer.systemTests(),
                analyzer.stagingTests(),
                analyzer.stagingSetupTests(),
                analyzer.productionTests());
        writeDescriptorFile(descriptor);
    }

    private void analyzeTestClasses(TestAnnotationAnalyzer analyzer) throws MojoExecutionException {
        if (! Files.exists(testClassesDirectory())) return;

        try (Stream<Path> files = Files.walk(testClassesDirectory())) {
            files
                    .filter(f -> f.toString().endsWith(".class"))
                    .forEach(analyzer::analyzeClass);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze test classes: " + e.getMessage(), e);
        }
    }

    private void writeDescriptorFile(TestDescriptor descriptor) throws MojoExecutionException {
        try {
            Path descriptorFile = testClassesDirectory().resolve(TestDescriptor.DEFAULT_FILENAME);
            Files.createDirectories(descriptorFile.getParent());
            Files.write(descriptorFile, descriptor.toJson().getBytes(UTF_8));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write test descriptor file: " + e.getMessage(), e);
        }
    }

    private Path testClassesDirectory() { return Paths.get(project.getBuild().getTestOutputDirectory()); }
}
