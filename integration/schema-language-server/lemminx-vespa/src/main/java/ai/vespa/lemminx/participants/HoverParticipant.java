package ai.vespa.lemminx.participants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.services.extensions.hover.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.hover.IHoverRequest;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

/**
 * TODO: refactor and handle tags with the same name based on context.
 */
public class HoverParticipant implements IHoverParticipant {
    private static final Logger logger = Logger.getLogger(HoverParticipant.class.getName());
    private Path serverPath;

    public HoverParticipant(Path serverPath) {
        this.serverPath = serverPath;
    }

    @Override
    public Hover onTag(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        if (request.getCurrentTag() == null) return null;

        DOMNode iterator = request.getNode();

        List<String> nodePath = new LinkedList<>();
        while (iterator != null) {
            nodePath.add(0, iterator.getNodeName());
            iterator = iterator.getParentNode();
        }

        Optional<MarkupContent> content = getFileHover(request.getCurrentTag(), nodePath);

        if (content.isEmpty()) return null;

        return new Hover(content.get(), request.getHoverRange());
    }

    @Override
    public Hover onAttributeName(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        return null;
    }

    @Override
    public Hover onAttributeValue(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        return null;
    }

    @Override
    public Hover onText(IHoverRequest request, CancelChecker cancelChecker) throws Exception {
        return null;
    }

    private Optional<MarkupContent> getFileHover(String tagName, List<String> pathElements) {
        Path readPath = serverPath.resolve("hover");

        if (!serverPath.toFile().exists()) {
            logger.warning("Could not get hover content because services hover does not exist!");
            return Optional.empty();
        }

        for (String element : pathElements) {
            if (readPath.resolve(element).toFile().exists()) {
                readPath = readPath.resolve(element);
            }
        }

        readPath = readPath.resolve(tagName + ".md");

        if (!readPath.toFile().exists()) {
            logger.warning(readPath.toString() + " did not exist!");
            return Optional.empty();
        }

        try {
            String content = Files.readString(readPath);
            MarkupContent mdContent = new MarkupContent(MarkupKind.MARKDOWN, content);
            return Optional.of(mdContent);
        } catch (Exception ex) {
            logger.severe("Unknown exception: " + ex.getMessage());
        }
        return Optional.empty();
    }
}
