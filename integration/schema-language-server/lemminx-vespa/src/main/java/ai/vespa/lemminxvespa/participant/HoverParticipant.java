package ai.vespa.lemminxvespa.participant;

import java.io.PrintStream;

import org.eclipse.lemminx.services.extensions.hover.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.hover.IHoverRequest;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

/**
 * HoverParticipant
 */
public class HoverParticipant implements IHoverParticipant {
    PrintStream logger;

    public HoverParticipant(PrintStream logger) {
        this.logger = logger;
    }


	@Override
	public Hover onAttributeName(IHoverRequest request, CancelChecker monitor) throws Exception {
        logger.println("On attr name");
        return null;
	}

	@Override
	public Hover onAttributeValue(IHoverRequest request, CancelChecker monitor) throws Exception {
        logger.println("On attr value");
        return null;
	}

	@Override
	public Hover onTag(IHoverRequest request, CancelChecker monitor) throws Exception {
        logger.println("On tag");
        return null;
	}

	@Override
	public Hover onText(IHoverRequest request, CancelChecker monitor) throws Exception {
        logger.println("On text");
        return null;
	}
}
