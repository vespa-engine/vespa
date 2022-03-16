// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import com.yahoo.text.XML;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Element;

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
        OptionalInt allowMajor = majorVersion(Paths.get("src/main/application/deployment.xml"));
        String compileVersion = controller.compileVersion(id, allowMajor);
        if (allowMajor.isPresent()) {
            getLog().info("Allowing major version " + allowMajor.getAsInt() + ".");
        }
        getLog().info("Vespa version to compile against is '" + compileVersion + "'.");
        getLog().info("Writing compile version to '" + output + "'.");
        Files.createDirectories(output.getParent());
        Files.writeString(output, compileVersion);
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
