package ai.vespa.lemminx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;

public class VespaPlugin implements IXMLExtension {

    PrintStream logger;
    HoverParticipant hoverParticipant;

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
        } catch (Exception ex) {
        }

        hoverParticipant = new HoverParticipant(logger);
        registry.registerHoverParticipant(hoverParticipant);
	}

	@Override
	public void stop(XMLExtensionsRegistry registry) {
		// Unregister here completion, hover, etc participants
        if (hoverParticipant != null) {
            registry.unregisterHoverParticipant(hoverParticipant);
        }
	}
}
