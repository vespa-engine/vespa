package ai.vespa.hosted.plugin;

import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Finds the Vespa version to compile against, for a hosted Vespa application, and writes it to target/compile.version
 *
 * @author jonmv
 */
@Mojo(name = "compileVersion")
public class CompileVersionMojo extends AbstractVespaMojo {

    @Override
    protected void doExecute() throws IOException {
        Files.createDirectories(Paths.get(projectPathOf("target")));
        Files.writeString(Paths.get(projectPathOf("target", "compile.version")), controller.compileVersion(id));
    }

}
