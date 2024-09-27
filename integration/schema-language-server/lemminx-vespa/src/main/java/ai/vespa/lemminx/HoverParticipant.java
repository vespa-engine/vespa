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
        logger.println("Tag: " + (request.getCurrentTag() == null ? "NULL" : request.getCurrentTag()));
        return null;
    }

    @Override
    public Hover onAttributeName(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        logger.println("Attribute name: " + request.getCurrentAttributeName());
        return null;
    }

    @Override
    public Hover onAttributeValue(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        logger.println("Attribute value: " + request.getCurrentAttribute().getValue());
        return null;
    }

    @Override
    public Hover onText(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        logger.println("Text: " + request.getNode().toString());
        return null;
    }
}
