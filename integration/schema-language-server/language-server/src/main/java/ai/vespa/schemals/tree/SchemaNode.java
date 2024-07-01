package ai.vespa.schemals.tree;

import java.util.ArrayList;

import javax.xml.validation.Schema;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.*;

public class SchemaNode {

    private Token.TokenType type;
    private String identifierString;
    private SchemaNode parent;
    private boolean isUserDefinedIdentifier = false;
    private Node originalNode;

    // This array has to be in order, without overlapping elements
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

    // Return token type (if the node is a token), even if the node is dirty
    public Token.TokenType getDirtyType() {
        Node.NodeType originalType = originalNode.getType();
        if (originalType instanceof Token.TokenType)return (Token.TokenType)originalType;
        return null;
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

    public String getIdentifierString() {
        return identifierString;
    }

    public String getClassLeafIdentifierString() {
        int lastIndex = identifierString.lastIndexOf('.');
        return identifierString.substring(lastIndex + 1);
    }

    public Range getRange() {
        return range;
    }

    public SchemaNode getParent() {
        return parent;
    }

    public SchemaNode getPrevious() {
        if (parent == null)return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1)return null; // invalid setup

        if (parentIndex == 0)return parent;
        return parent.get(parentIndex - 1);
    }

    public int indexOf(SchemaNode child) {
        return this.children.indexOf(child);
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

    public boolean isLeaf() {
        return children.size() == 0;
    }

    public boolean isDirty() {
        return originalNode.isDirty();
    }
}
