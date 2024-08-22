package ai.vespa.lemminxvespa;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PipedReader;
import java.io.PrintStream;
import java.util.logging.Logger;

import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lsp4j.InitializeParams;

/**
 * ServicesPlugin
 */
public class VespaExtension implements IXMLExtension {
    PrintStream logger = System.out;

	@Override
	public void doSave(ISaveContext arg0) {
	}

	@Override
	public void start(InitializeParams params, XMLExtensionsRegistry registry) {
        try {
            logger = new PrintStream(new FileOutputStream("/Users/magnus/repos/integrationtest7/log.txt", true));
            logger.println("Asserted dominance.");
        } catch(Exception ex) {
        }
	}

	@Override
	public void stop(XMLExtensionsRegistry arg0) {
	}
}
