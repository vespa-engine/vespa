package ai.vespa.lemminx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.lemminx.services.IXMLValidationService;
import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lemminx.uriresolver.URIResolverExtension;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;

public class VespaPlugin implements IXMLExtension {

    PrintStream logger;
    HoverParticipant hoverParticipant;
    IXMLValidationService validationService;
    URIResolverExtension uriResolverExtension;
    Path serverPath;

    @Override
	public void doSave(ISaveContext context) {
		// Called when settings or XML document are saved.
	}

	@Override
	public void start(InitializeParams params, XMLExtensionsRegistry registry) {
		// Register here completion, hover, etc participants
        try {
            logger = new PrintStream(new FileOutputStream("/Users/magnus/repos/integrationtest7/log.txt", true));
            logger.println("Asserted dominance");
            serverPath = Paths.get(VespaPlugin.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();

            UnpackRNGFiles.unpackRNGFiles(
                serverPath,
                logger
            );
        } catch (Exception ex) {
            if (logger != null) {
                logger.println("Exception occured during start: " + ex.getMessage());
            }
            return;
        }

        hoverParticipant = new HoverParticipant(logger);
        validationService = new ValidationService(logger);
        uriResolverExtension = new ServicesURIResolverExtension(serverPath, logger);
        registry.getResolverExtensionManager().registerResolver(uriResolverExtension);
        registry.registerHoverParticipant(hoverParticipant);
        registry.setValidationService(validationService);


	}

	@Override
	public void stop(XMLExtensionsRegistry registry) {
		// Unregister here completion, hover, etc participants
        if (hoverParticipant != null) {
            registry.unregisterHoverParticipant(hoverParticipant);
        }

        if (validationService != null) {
            registry.setValidationService(null);
        }
	}
}
