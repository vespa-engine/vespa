package ai.vespa.lemminx.index;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMNode;

import ai.vespa.lemminx.command.SchemaLSCommands;

public class ServiceDocument {
    private static final Logger logger = Logger.getLogger(ServiceDocument.class.getName());

    private record Component(String componentId, int start, int end) {
    }

    public void didChange(DOMDocument document) {
        // printTree(document.getChildren());

        List<Component> components = findComponents(document);
        String componentsIds = "[";

        for (Component cmp : components) {
            componentsIds += "\"" + cmp.componentId() + "\",";
        }
        componentsIds = componentsIds.substring(0, componentsIds.length() - 1) + "]";
        SchemaLSCommands.instance().sendComponentIds(componentsIds);

    }

    private List<Component> findComponents(DOMDocument document) {
        List<DOMNode> rootChildren = document.getChildren();
        List<DOMNode> domComponents = new ArrayList<>();
        for (DOMNode child : rootChildren) {
            domComponents.addAll(traverseForComponents(child));
        }

        List<Component> components = new ArrayList<>();

        for (DOMNode component : domComponents) {
            components.add(new Component(
                    component.getAttribute("id"),
                    component.getStart(),
                    component.getEnd()));
        }

        return components;
    }

    private List<DOMNode> traverseForComponents(DOMNode node) {
        if (node.getNodeName().equals("component")) {
            return List.of(node);
        }

        List<DOMNode> ret = new ArrayList<>();
        for (DOMNode child : node.getChildren()) {
            ret.addAll(traverseForComponents(child));
        }
        return ret;
    }

    private static void printTree(List<DOMNode> nodes) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream pStream = new PrintStream(outputStream);
        pStream.println();

        for (DOMNode node : nodes) {
            treeString(pStream, 0, node);
        }

        logger.info(outputStream.toString());
    }

    private static void treeString(PrintStream pStream, int indent, DOMNode node) {
        pStream.println(new String(new char[indent]).replace("\0", "  ") + node.getNodeName());
        for (DOMNode child : node.getChildren()) {
            treeString(pStream, indent + 1, child);
        }
    }
}
