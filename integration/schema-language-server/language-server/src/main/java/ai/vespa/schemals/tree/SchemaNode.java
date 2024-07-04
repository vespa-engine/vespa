package ai.vespa.schemals.tree;

import java.util.ArrayList;

import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.Token.ParseExceptionSource;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.indexingElm;

public class SchemaNode {

    private TokenType type;
    private String identifierString;
    private SchemaNode parent;
    private boolean isUserDefinedIdentifier = false;
    private boolean isSchemaType = false;
    private Node originalNode;

    private ai.vespa.schemals.parser.indexinglanguage.Node indexingNode;

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
        type = (node.isDirty() || !(originalType instanceof TokenType)) ? null : (TokenType) originalType;

        identifierString = node.getClass().getName();
        range = CSTUtils.getNodeRange(node);

        for (Node child : node) {
            children.add(new SchemaNode(child, this));
        }
        
    }

    public TokenType getType() {
        return type;
    }

    // Return token type (if the node is a token), even if the node is dirty
    public TokenType getDirtyType() {
        Node.NodeType originalType = originalNode.getType();
        if (originalType instanceof TokenType)return (TokenType)originalType;
        return null;
    }

    public TokenType setType(TokenType type) {
        this.type = type;
        return type;
    }

    public void setUserDefinedIdentifier() {
        setType(TokenType.IDENTIFIER);
        this.isUserDefinedIdentifier = true;
    }

    public boolean isUserDefinedIdentifier() {
        return isUserDefinedIdentifier;
    }

    public void setSchemaType() {
        this.isSchemaType = true;
    }

    public boolean isSchemaType() {
        return isSchemaType;
    }

    public boolean isIndexingElm() {
        return (originalNode instanceof indexingElm);
    }

    public String getILScript() {
        if (!isIndexingElm())return null;
        indexingElm elmNode = (indexingElm)originalNode;
        return elmNode.getILScript();
    }

    public boolean hasIndexingNode() {
        return this.indexingNode != null;
    }

    public ai.vespa.schemals.parser.indexinglanguage.Node getIndexingNode() {
        return this.indexingNode;
    }

    public void setIndexingNode(ai.vespa.schemals.parser.indexinglanguage.Node node) {
        this.indexingNode = node;
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

    public IllegalArgumentException getIllegalArgumentException() {
        if (originalNode instanceof Token) {
            return ((Token)originalNode).getIllegalArguemntException();
        }
        return null;
    }

    public ParseExceptionSource getParseExceptionSource() {
        if (originalNode instanceof Token) {
            return ((Token)originalNode).getParseExceptionSource();
        }
        return null;
    }

    public TokenSource getTokenSource() { return originalNode.getTokenSource(); }

    public String toString() {
        return getText() + " [" + isSchemaType + "]";
    }
}
