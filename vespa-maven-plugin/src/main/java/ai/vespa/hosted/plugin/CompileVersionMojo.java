package ai.vespa.hosted.plugin;

import org.apache.maven.plugins.annotations.Mojo;

/**
 * Finds the Vespa version to compile against, for a hosted Vespa application.
 *
 * @author jonmv
 */
@Mojo(name = "compileVersion")
public class CompileVersionMojo extends AbstractVespaMojo {

    @Override
    protected void doExecute() {
        System.out.println(controller.compileVersion(id));
    }

}
