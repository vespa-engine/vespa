package ai.vespa.lemminxvespa;

import java.io.FileOutputStream;
import java.io.PrintStream;

import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lsp4j.InitializeParams;

import ai.vespa.lemminxvespa.participant.DiagnosticsParticipant;
import ai.vespa.lemminxvespa.participant.HoverParticipant;

/**
 * ServicesPlugin
 * TODO: Figure out class loading of lemminx stuff.
 * TODO: Fetch *.xsd files e.g. ./config-model/target/generated-sources/trang/resources/schema/services.xsd
 *       and use to validate.
 */
public class VespaExtension implements IXMLExtension {
    PrintStream logger = System.out;

    private IDiagnosticsParticipant diagnosticsParticipant;
    //private IHoverParticipant hoverParticipant;

	@Override
	public void doSave(ISaveContext saveContext) {
	}

	@Override
	public void start(InitializeParams params, XMLExtensionsRegistry registry) {
        try {
            logger = new PrintStream(new FileOutputStream("/Users/magnus/repos/integrationtest7/log.txt", true));
            logger.println("Asserted dominance.");
        } catch(Exception ex) {
        }

        diagnosticsParticipant = new DiagnosticsParticipant(this.logger);
        registry.registerDiagnosticsParticipant(diagnosticsParticipant);

        //hoverParticipant = new HoverParticipant(this.logger);
        //registry.registerHoverParticipant(hoverParticipant);
	}

	@Override
	public void stop(XMLExtensionsRegistry registry) {
        registry.unregisterDiagnosticsParticipant(diagnosticsParticipant);
        //registry.unregisterHoverParticipant(hoverParticipant);
	}
}
