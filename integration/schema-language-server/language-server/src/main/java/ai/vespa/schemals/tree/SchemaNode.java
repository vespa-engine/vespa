package ai.vespa.schemals.tree;

import java.util.ArrayList;

import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.*;

public class SchemaNode {

    private Node.NodeType type;
    private String identifierString;
    private SchemaNode parent;

    private ArrayList<SchemaNode> children = new ArrayList<SchemaNode>();

    private Range range;

    public SchemaNode(Node node) {
        this(node, null);
    }
    
    private SchemaNode(Node node, SchemaNode parent) {
        this.parent = parent;
        type = node.getType();
        identifierString = node.getClass().getName();
        range = CSTUtils.getNodeRange(node);

        for (Node child : node) {
            children.add(new SchemaNode(child, this));
        }
        
    }

    public Node.NodeType getType() {
        return type;
    }

    public String getIdentifierString() {
        return identifierString;
    }

    public Range getRange() {
        return range;
    }

    public SchemaNode getParent() {
        return parent;
    }

    public int size() {
        return children.size();
    }

    public SchemaNode get(int i) {
        return children.get(i);
    }
}
