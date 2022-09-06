// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import com.yahoo.component.Version;
import com.yahoo.text.XML;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.OptionalInt;

/**
 * Finds the Vespa version to compile against, for a hosted Vespa application.
 *
 * @author jonmv
 */
@Mojo(name = "compileVersion")
public class CompileVersionMojo extends AbstractVespaMojo {

    @Parameter(property = "outputFile", defaultValue = "target/vespa.compile.version")
    private String outputFile;

    @Override
    protected void doExecute() throws IOException {
        Path output = Paths.get(outputFile).toAbsolutePath();
        OptionalInt allowMajor = majorVersion(new File(project.getBasedir(), "src/main/application/deployment.xml").toPath());
        allowMajor.ifPresent(major -> getLog().info("Allowing only major version " + major + "."));

        Version compileVersion = Version.fromString(controller.compileVersion(id, allowMajor));

        MavenProject current = project;
        while (current.getParent() != null && current.getParent().getParentArtifact() != null)
            current = current.getParent();

        Version parentVersion;
        Artifact parentArtifact = current.getParentArtifact();
        if (parentArtifact != null && parentArtifact.getGroupId().matches("(com\\.yahoo\\.vespa|ai\\.vespa)(\\..+)?")) {
            parentVersion = Version.fromString(parentArtifact.getVersion());
            if (parentVersion.compareTo(compileVersion) < 0)
                throw new IllegalArgumentException("compile version (" + compileVersion + ") cannot be higher than parent version (" + parentVersion + ")");
        }

        getLog().info("Vespa version to compile against is '" + compileVersion.toFullString() + "'.");
        getLog().info("Writing compile version to '" + output + "'.");
        Files.createDirectories(output.getParent());
        Files.writeString(output, compileVersion.toFullString());
    }

    /** Returns the major version declared in given deploymentXml, if any */
    static OptionalInt majorVersion(Path deploymentXml) {
        try {
            String xml = Files.readString(deploymentXml);
            Element deploymentTag = XML.getDocument(xml).getDocumentElement();
            if (deploymentTag == null) return OptionalInt.empty();
            String allowMajor = deploymentTag.getAttribute("major-version");
            if (allowMajor.isEmpty()) return OptionalInt.empty();
            return OptionalInt.of(parseMajor(allowMajor));
        } catch (NoSuchFileException ignored) {
            return OptionalInt.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int parseMajor(String s) {
        try {
            int major = Integer.parseInt(s);
            if (major < 1) throw new IllegalArgumentException("Major version must be positive, got " + major);
            return major;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid major version '" + s + "'", e);
        }
    }

}
