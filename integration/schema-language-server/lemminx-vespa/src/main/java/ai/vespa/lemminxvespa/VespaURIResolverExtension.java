package ai.vespa.lemminxvespa;

import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.lemminx.uriresolver.CacheResourcesManager;
import org.eclipse.lemminx.uriresolver.URIResolverExtension;
import org.eclipse.lemminx.uriresolver.CacheResourcesManager.ResourceToDeploy;

/**
 * VespaURIResolverExtension
 */
public class VespaURIResolverExtension implements URIResolverExtension {

    private PrintStream logger;
    private ResourceToDeploy xmlSchema;

    public VespaURIResolverExtension(PrintStream logger) {
        this.logger = logger;
        //try {
        //    URI uri = URI.create("file:///Users/magnus/repos/vespa/config-model/target/generated-sources/trang/resources/schema/services.xsd");
        //    xmlSchema = new ResourceToDeploy(uri, "/schemas/vespa/services.xsd");
        //} catch (Exception e) {
        //    logger.println("[ERROR] " + e.getMessage());
        //    e.printStackTrace(logger);
        //}
    }

	@Override
	public String resolve(String baseLocation, String publicId, String systemId) {
        logger.println("Resolve: " + baseLocation + ", " + publicId + ", " + systemId);
        try {
            //Path outFile = CacheResourcesManager.getResourceCachePath(xmlSchema);
            //String res = outFile.toFile().toURI().toString();
            //logger.println("Return from cache: " + res);
            return new File("/Users/magnus/repos/vespa/config-model/target/generated-sources/trang/resources/schema/services.xsd").toURI().toString();
        } catch (Exception e) {
            logger.println("[ERROR]: " + e.getMessage());
        }
        return null;
	}
}
