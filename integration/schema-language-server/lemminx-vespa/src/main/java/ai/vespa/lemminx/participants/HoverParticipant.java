package ai.vespa.lemminx.participants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

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

        Optional<MarkupContent> content = getFileHover(request.getCurrentTag());

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

    private Optional<MarkupContent> getFileHover(String tagName) {
        Path servicesPath = serverPath.resolve("hover").resolve("services");

        if (!serverPath.toFile().exists()) {
            logger.warning("Could not get hover content because services hover does not exist!");
            return Optional.empty();
        }

        // key: tag -> value: path
        Map<String, Path> markdownFiles = new HashMap<>();

        try (Stream<Path> stream = Files.list(servicesPath)) {
            stream.forEach(path -> {
                if (Files.isDirectory(path)) {
                    try (Stream<Path> innerStream = Files.list(path)) {
                        innerStream.forEach(innerPath -> {
                            String tag = innerPath.getFileName().toString();
                            if (tag.endsWith(".md")) {
                                tag = tag.substring(0, tag.length() - 3);
                                if (markdownFiles.containsKey(tag)) {
                                    //logger.warning("Duplicate key: " + tag);
                                }
                                markdownFiles.put(tag, innerPath);
                            }
                        });
                    } catch (IOException ex) { 
                        // Ignore
                    }
                } else {
                    String tag = path.getFileName().toString();
                    if (tag.endsWith(".md")) {
                        tag = tag.substring(0, tag.length() - 3);
                        if (markdownFiles.containsKey(tag)) {
                            //logger.warning("Duplicate key: " + tag);
                        }
                        markdownFiles.put(tag, path);
                    }
                }
            });
        } catch (IOException ex) {
            logger.severe("Failed to read documentation files: " + ex.getMessage());
        }

        if (!markdownFiles.containsKey(tagName)) {
            logger.warning("Found no hover file with name " + tagName + ".md");
            return Optional.empty();
        }

        try {
            Path markdownPath = markdownFiles.get(tagName);
            String markdown = Files.readString(markdownPath);

            MarkupContent mdContent = new MarkupContent(MarkupKind.MARKDOWN, markdown);
            return Optional.of(mdContent);
        } catch (Exception ex) {
            logger.severe("Unknown error when getting hover: " + ex.getMessage());
        }

        return Optional.empty();
    }
}
