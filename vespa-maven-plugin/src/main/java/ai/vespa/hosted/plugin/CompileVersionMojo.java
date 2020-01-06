// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        String compileVersion = controller.compileVersion(id);
        getLog().info("Vespa version to compile against is '" + compileVersion + "'.");
        getLog().info("Writing compile version to '" + output + "'.");
        Files.createDirectories(output.getParent());
        Files.writeString(output, compileVersion);
    }

}
