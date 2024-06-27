package ai.vespa.schemals.tree;

import java.util.ArrayList;

import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.*;

public class SchemaNode {

    private Token.TokenType type;
    private String identifierString;
    private SchemaNode parent;
    private boolean isUserDefinedIdentifier = false;
    private SchemaNode refersTo = null;
    private Node originalNode;

    private ArrayList<SchemaNode> children = new ArrayList<SchemaNode>();

    private Range range;

    public SchemaNode(Node node) {
        this(node, null);
    }
    
    private SchemaNode(Node node, SchemaNode parent) {
        this.parent = parent;
        originalNode = node;
        Node.NodeType originalType = node.getType();
        type = (node.isDirty() || !(originalType instanceof Token.TokenType)) ? null : (Token.TokenType) originalType;

        identifierString = node.getClass().getName();
        range = CSTUtils.getNodeRange(node);

        for (Node child : node) {
            children.add(new SchemaNode(child, this));
        }
        
    }

    public Token.TokenType getType() {
        return type;
    }

    public Token.TokenType setType(Token.TokenType type) {
        this.type = type;
        return type;
    }

    public void setUserDefinedIdentifier() {
        setType(Token.TokenType.IDENTIFIER);
        this.isUserDefinedIdentifier = true;
    }

    public boolean isUserDefinedIdentifier() {
        return isUserDefinedIdentifier;
    }

    public SchemaNode getRefersTo() {
        return refersTo;
    }

    public SchemaNode setRefersTo(SchemaNode node) {
        refersTo = node;
        return node;
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

    public String getText() {
        return originalNode.getSource();
    }
}
