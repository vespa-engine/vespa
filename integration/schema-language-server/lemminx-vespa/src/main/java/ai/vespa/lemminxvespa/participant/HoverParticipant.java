package ai.vespa.lemminxvespa.participant;

import java.io.PrintStream;

import org.eclipse.lemminx.services.extensions.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.IHoverRequest;
import org.eclipse.lsp4j.Hover;

/**
 * HoverParticipant
 */
public class HoverParticipant implements IHoverParticipant {
    PrintStream logger;

    public HoverParticipant(PrintStream logger) {
        this.logger = logger;
    }

	@Override
	public Hover onTag(IHoverRequest request) throws Exception {
        logger.println("On tag");
        return null;
	}

	@Override
	public Hover onAttributeName(IHoverRequest request) throws Exception {
        logger.println("On attr name");
        return null;
	}

	@Override
	public Hover onAttributeValue(IHoverRequest request) throws Exception {
        logger.println("On attr value");
        return null;
	}

	@Override
	public Hover onText(IHoverRequest request) throws Exception {
        logger.println("On text");
        return null;
	}
}
