package ai.vespa.lemminx;

import org.eclipse.lemminx.uriresolver.URIResolverExtension;

public class ServicesURIResolverExtension implements URIResolverExtension {

    @Override
    public String resolve(String baseLocation, String publicId, String systemId) {
        return "file:///Users/magnus/repos/vespa/config-model/target/generated-sources/trang/resources/schema/services.rng";
    }
}
