package ai.vespa.schemals.tree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.Node.NodeType;
import ai.vespa.schemals.parser.Token.ParseExceptionSource;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.SubLanguageData;
import ai.vespa.schemals.parser.ast.indexingElm;
import ai.vespa.schemals.tree.indexinglanguage.ILUtils;
import ai.vespa.schemals.tree.rankingexpression.RankingExpressionUtils;
import ai.vespa.schemals.parser.ast.expression;
import ai.vespa.schemals.parser.ast.featureListElm;

public class SchemaNode implements Iterable<SchemaNode> {

    public enum LanguageType {
        SCHEMA,
        INDEXING,
        RANK_EXPRESSION,
        CUSTOM
    }

    private LanguageType language;
    private String identifierString;
    private Range range;
    private boolean isDirty = false;

    private Symbol symbolAtNode;
    
    // This array has to be in order, without overlapping elements
    private ArrayList<SchemaNode> children = new ArrayList<SchemaNode>();
    private SchemaNode parent;
    
    private Node originalSchemaNode;
    private ai.vespa.schemals.parser.indexinglanguage.Node originalIndexingNode;
    private ai.vespa.schemals.parser.rankingexpression.Node originalRankExpressionNode;

    // Special properties for node in the CUSTOM language
    private String contentString;
    private Class<? extends Node> simulatedSchimaClass;

    // Special features for nodes in the Schema language
    private TokenType schemaType;
    private ai.vespa.schemals.parser.rankingexpression.Token.TokenType rankExpressionType;

    private SchemaNode(LanguageType language, Range range, String identifierString, boolean isDirty) {
        this.language = language;
        this.range = range;
        this.identifierString = identifierString;
        this.isDirty = isDirty;
    }

    // To create tokens outside the other languages
    public SchemaNode(Range range, String contentString, String identifierString) {
        this(LanguageType.CUSTOM, range, identifierString, false);
        this.contentString = contentString;
    }
    
    public SchemaNode(Node node) {
        this(
            LanguageType.SCHEMA,
            CSTUtils.getNodeRange(node),
            node.getClass().getName(),
            node.isDirty()
        );

        this.originalSchemaNode = node;
        this.schemaType = calculateSchemaType();

        for (var child : node) {
            SchemaNode newNode = new SchemaNode(child);
            newNode.setParent(this);
            children.add(newNode);
        }
    }

    public SchemaNode(ai.vespa.schemals.parser.indexinglanguage.Node node, Position rangeOffset) {
        this(
            LanguageType.INDEXING,
            CSTUtils.addPositionToRange(rangeOffset, ILUtils.getNodeRange(node)),
            node.getClass().getName(),
            node.isDirty()
        );

        this.originalIndexingNode = node;

        for (var child : node) {
            SchemaNode newNode = new SchemaNode(child, rangeOffset);
            newNode.setParent(this);
            children.add(newNode);
        }

    }

    public SchemaNode(ai.vespa.schemals.parser.rankingexpression.Node node, Position rangeOffset) {
        this(
            LanguageType.RANK_EXPRESSION,
            CSTUtils.addPositionToRange(rangeOffset, RankingExpressionUtils.getNodeRange(node)),
            node.getClass().getName(),
            node.isDirty()
        );

        this.originalRankExpressionNode = node;
        if (node instanceof ai.vespa.schemals.parser.rankingexpression.Token) {
            this.rankExpressionType = (ai.vespa.schemals.parser.rankingexpression.Token.TokenType) node.getType();
        }

        for (var child : node) {
            SchemaNode newNode = new SchemaNode(child, rangeOffset);
            newNode.setParent(this);
            children.add(newNode);
        }

    }

    private void setParent(SchemaNode parent) {
        this.parent = parent;
    }

    public void addChild(SchemaNode child) {
        child.setParent(this);
        this.children.add(child);
    }

    public void addChildren(List<SchemaNode> children) {
        for (SchemaNode child : children) {
            addChild(child);
        }
    }

    public void clearChildren() {
        for (SchemaNode child : children) {
            child.setParent(null);
        }

        children.clear();
    }

    private TokenType calculateSchemaType() {
        if (language != LanguageType.SCHEMA) return null;
        if (isDirty) return null;

        NodeType nodeType = originalSchemaNode.getType();
        if (!(nodeType instanceof TokenType)) return null;
        return (TokenType)nodeType;
    }

    public TokenType getSchemaType() {
        
        return schemaType;
    }

    public ai.vespa.schemals.parser.rankingexpression.Token.TokenType getRankExpressionType() {
        return rankExpressionType;
    }

    // Return token type (if the node is a token), even if the node is dirty
    public TokenType getDirtyType() {
        if (language != LanguageType.SCHEMA) return null;
        Node.NodeType originalType = originalSchemaNode.getType();
        if (originalType instanceof TokenType)return (TokenType)originalType;
        return null;
    }

    public TokenType setSchemaType(TokenType type) {
        if (language != LanguageType.SCHEMA && language != LanguageType.CUSTOM) return null;

        this.schemaType = type;
        
        return type;
    }

    public void setSymbol(SymbolType type, String fileURI) {
        if (this.hasSymbol()) {
            throw new IllegalArgumentException("Cannot set symbol for node: " + this.toString() + ". Already has symbol.");
        }
        this.symbolAtNode = new Symbol(this, type, fileURI);
    }

    public void setSymbol(SymbolType type, String fileURI, Symbol scope) {
        if (this.hasSymbol()) {
            throw new IllegalArgumentException("Cannot set symbol for node: " + this.toString() + ". Already has symbol.");
        }
        this.symbolAtNode = new Symbol(this, type, fileURI, scope);
    }

    public void setSymbol(SymbolType type, String fileURI, Symbol scope, String shortIdentifier) {
        if (this.hasSymbol()) {
            throw new IllegalArgumentException("Cannot set symbol for node: " + this.toString() + ". Already has symbol.");
        }
        this.symbolAtNode = new Symbol(this, type, fileURI, scope, shortIdentifier);
    }

    public void removeSymbol() {
        this.symbolAtNode = null;
    }

    public void setSymbolType(SymbolType newType) {
        if (!this.hasSymbol()) return;
        this.symbolAtNode.setType(newType);
    }

    public void setSymbolStatus(SymbolStatus newStatus) {
        if (!this.hasSymbol()) return;
        this.symbolAtNode.setStatus(newStatus);
    }

    public boolean hasSymbol() {
        return this.symbolAtNode != null;
    }

    public Symbol getSymbol() {
        if (!hasSymbol()) throw new IllegalArgumentException("get Symbol called on node without a symbol!");
        return this.symbolAtNode;
    }

    public boolean containsOtherLanguageData(LanguageType language) {
        if (this.language != LanguageType.SCHEMA) return false;

        return (
            (language == LanguageType.INDEXING && originalSchemaNode instanceof indexingElm) ||
            (language == LanguageType.RANK_EXPRESSION && (
                (originalSchemaNode instanceof featureListElm) ||
                (originalSchemaNode instanceof expression)  
            ))
        );
    }

    public boolean containsExpressionData() {
        if (this.language != LanguageType.SCHEMA) return false;

        return (originalSchemaNode instanceof expression);
    }

    public SubLanguageData getILScript() {
        if (!containsOtherLanguageData(LanguageType.INDEXING)) return null;
        indexingElm elmNode = (indexingElm)originalSchemaNode;
        return elmNode.getILScript();
    }

    public String getRankExpressionString() {
        if (!containsOtherLanguageData(LanguageType.RANK_EXPRESSION)) return null;

        if (originalSchemaNode instanceof featureListElm) {
            featureListElm elmNode = (featureListElm)originalSchemaNode;
            return elmNode.getFeatureListString();
        }

        expression expressionNode = (expression)originalSchemaNode;
        return expressionNode.getExpressionString();
    }

    public boolean hasIndexingNode() {
        return false; // this.indexingNode != null;
    }

    public boolean hasRankExpressionNode() {
        return false; // this.rankExpressionNode != null;
    }

    public Node getOriginalSchemaNode() {
        return originalSchemaNode;
    }

    public ai.vespa.schemals.parser.indexinglanguage.Node getOriginalIndexingNode() {
        return this.originalIndexingNode;
    }

    public ai.vespa.schemals.parser.rankingexpression.Node getOriginalRankExpressionNode() {
        return this.originalRankExpressionNode;
    }

    public void setSimulatedASTClass(Class<? extends Node> astClass) {
        if (language != LanguageType.CUSTOM) throw new IllegalArgumentException("Cannot set Simulated AST Class on a Schema node of type other than Custom");

        simulatedSchimaClass = astClass;
    }

    public boolean isASTInstance(Class<?> astClass) {
        if (language == LanguageType.CUSTOM && astClass.isInstance(simulatedSchimaClass)) return true;
        if (language == LanguageType.SCHEMA) return astClass.isInstance(originalSchemaNode);
        if (language == LanguageType.RANK_EXPRESSION) return astClass.isInstance(originalRankExpressionNode);
        if (language == LanguageType.INDEXING) return astClass.isInstance(originalIndexingNode);
        return false;
    }

    public boolean isSchemaASTInstance(Class<? extends Node> astClass) {
        if (language == LanguageType.CUSTOM) return astClass.equals(simulatedSchimaClass);
        
        return astClass.isInstance(originalSchemaNode);
    }

    public boolean isRankExpressionASTInstance(Class<? extends ai.vespa.schemals.parser.rankingexpression.Node> astClass) {
        return astClass.isInstance(originalRankExpressionNode);
    }

    public Class<?> getASTClass() {
        if (language == LanguageType.CUSTOM) return simulatedSchimaClass;

        if (language == LanguageType.SCHEMA && originalSchemaNode != null) {
            return originalSchemaNode.getClass();
        }

        if (language == LanguageType.RANK_EXPRESSION && originalRankExpressionNode != null) {
            return originalRankExpressionNode.getClass();
        }

        if (language == LanguageType.INDEXING && originalIndexingNode != null) {
            return originalIndexingNode.getClass();
        }

        return null;
    }

    public String getIdentifierString() {
        return identifierString;
    }

    public void setNewStartCharacter(int startCharacter) {
        if (this.originalSchemaNode == null) return;
        int currentOffset = originalSchemaNode.getBeginOffset();
        int characterDelta = startCharacter - range.getStart().getCharacter();

        originalSchemaNode.setBeginOffset(currentOffset + characterDelta);
        this.range = CSTUtils.getNodeRange(originalSchemaNode);
    }

    public void setNewEndCharacter(int endCharacter) {
        if (originalSchemaNode == null) return;
        int currentOffset = originalSchemaNode.getEndOffset();
        int characterDelta = endCharacter - range.getEnd().getCharacter();

        originalSchemaNode.setEndOffset(currentOffset + characterDelta);
        this.range = CSTUtils.getNodeRange(originalSchemaNode);
    }

    public String getClassLeafIdentifierString() {
        int lastIndex = identifierString.lastIndexOf('.');
        return identifierString.substring(lastIndex + 1);
    }

    public Range getRange() {
        return range;
    }

    public SchemaNode getParent(int levels) {
        if (levels == 0) {
            return this;
        }

        if (parent == null) {
            return null;
        }

        return parent.getParent(levels - 1);
    }

    public SchemaNode getParent() {
        return getParent(1);
    }

    public void insertChildAfter(int index, SchemaNode child) {
        this.children.add(index+1, child);
        child.setParent(this);
    }

    public SchemaNode getPrevious() {
        if (parent == null)return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1)return null; // invalid setup

        if (parentIndex == 0)return parent;
        return parent.get(parentIndex - 1);
    }

    public SchemaNode getNext() {
        if (parent == null) return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1) return null;
        
        if (parentIndex == parent.size() - 1) return parent.getNext();

        return parent.get(parentIndex + 1);
    }

    private SchemaNode getSibling(int relativeIndex) {
        if (parent == null)return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1) return null; // invalid setup

        int siblingIndex = parentIndex + relativeIndex;
        if (siblingIndex < 0 || siblingIndex >= parent.size()) return null;
        
        return parent.get(siblingIndex);
    }

    public SchemaNode getPreviousSibling() {
        return getSibling(-1);
    }

    public SchemaNode getNextSibling() {
        return getSibling(1);
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
        
        if (language == LanguageType.SCHEMA) {
            return originalSchemaNode.getSource();
        }

        if (language == LanguageType.INDEXING) {
            return originalIndexingNode.getSource();
        }

        if (language == LanguageType.RANK_EXPRESSION) {
            return originalRankExpressionNode.getSource();
        }

        if (language == LanguageType.CUSTOM) {
            return contentString;
        }

        return null;
    }

    public boolean isLeaf() {
        return children.size() == 0;
    }

    public SchemaNode findFirstLeaf() {
        SchemaNode ret = this;
        while (ret.size() > 0) {
            ret = ret.get(0);
        }
        return ret;
    }

    public boolean getIsDirty() {
        return isDirty;
    }

    public IllegalArgumentException getIllegalArgumentException() {

        if (language == LanguageType.SCHEMA) {
            if (originalSchemaNode instanceof Token) {
                return ((Token)originalSchemaNode).getIllegalArguemntException();
            }
        }

        // if (language == LanguageType.INDEXING) {
        //     if (originalIndexingNode instanceof ai.vespa.schemals.parser.indexinglanguage.Token) {
        //         return ((ai.vespa.schemals.parser.indexinglanguage.Token)originalIndexingNode)
        //     }
        // }

        return null;
    }

    public ParseExceptionSource getParseExceptionSource() {
        if (language == LanguageType.SCHEMA) {
            if (originalSchemaNode instanceof Token) {
                return ((Token)originalSchemaNode).getParseExceptionSource();
            }
        }
        return null;
    }

    public TokenSource getTokenSource() {
        if (language == LanguageType.SCHEMA) {
            return originalSchemaNode.getTokenSource();
        }

        return null;
    }

    public LanguageType getLanguageType() { return language; }

    public String toString() {
        Position pos = getRange().getStart();
        String ret = "Node('" + getText() + "', [" + getASTClass() + "] at " + pos.getLine() + ":" + pos.getCharacter();
        if (hasSymbol()) {
            ret += " [SYMBOL " + getSymbol().getType().toString() + " " + getSymbol().getStatus().toString() + ": " + getSymbol().getLongIdentifier() +  "]";
        }
        ret += ")";
        return ret;
    }

	@Override
	public Iterator<SchemaNode> iterator() {
        return new Iterator<SchemaNode>() {
            int currentIndex = 0;

			@Override
			public boolean hasNext() {
                return currentIndex < children.size();
			}

			@Override
			public SchemaNode next() {
                return children.get(currentIndex++);
			}
        };
	}
}
