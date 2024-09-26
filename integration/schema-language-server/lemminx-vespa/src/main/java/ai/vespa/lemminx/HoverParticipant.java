package ai.vespa.lemminx;

import java.io.PrintStream;

import org.eclipse.lemminx.services.extensions.hover.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.hover.IHoverRequest;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class HoverParticipant implements IHoverParticipant {

    PrintStream logger;

    public HoverParticipant(PrintStream logger) {
        this.logger = logger;
    }

    @Override
    public Hover onTag(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        logger.println("On tag.");
        logger.println("    Request: " + request.toString());
        logger.println("    Tag: " + (request.getCurrentTag() == null ? "NULL" : request.getCurrentTag()));
        logger.println("    Node: " + (request.getNode() == null ? "NULL" : request.getNode().toString()));
        return null;
    }

    @Override
    public Hover onAttributeName(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        logger.println("On onAttributeName!");
        return null;
    }

    @Override
    public Hover onAttributeValue(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        logger.println("On onAttributeValue!");
        return null;
    }

    @Override
    public Hover onText(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        logger.println("On onText!");
        return null;
    }
}
