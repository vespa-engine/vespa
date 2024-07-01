package ai.vespa.schemals.hover;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.tree.SchemaNode;

public class SchemaHover {
    private static final String markdownPathRoot = "hover/";

    public static Hover getHover(EventPositionContext context) {

        SchemaNode node = context.document.getLeafNodeAtPosition(context.position);

        if (node == null) {
            return null;
        }

        String fileName = markdownPathRoot + node.getClassLeafIdentifierString() + ".md";

        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);

        if (inputStream == null) {
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        
        String markdown = reader.lines().collect(Collectors.joining(System.lineSeparator()));

        return new Hover(new MarkupContent("markdown", markdown), node.getRange());
    }
}
