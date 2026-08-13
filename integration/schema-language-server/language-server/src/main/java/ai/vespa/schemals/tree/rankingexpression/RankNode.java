package ai.vespa.schemals.tree.rankingexpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.rankingexpression.ast.BaseNode;
import ai.vespa.schemals.parser.rankingexpression.ast.COLON;
import ai.vespa.schemals.parser.rankingexpression.ast.IDENTIFIER;
import ai.vespa.schemals.parser.rankingexpression.ast.INTEGER;
import ai.vespa.schemals.parser.rankingexpression.ast.STRING;
import ai.vespa.schemals.parser.rankingexpression.ast.argExpression;
import ai.vespa.schemals.parser.rankingexpression.ast.identifierStr;
import ai.vespa.schemals.parser.rankingexpression.ast.tagValueStr;
import ai.vespa.schemals.parser.rankingexpression.ast.args;
import ai.vespa.schemals.parser.rankingexpression.ast.expression;
import ai.vespa.schemals.parser.rankingexpression.ast.feature;
import ai.vespa.schemals.parser.rankingexpression.ast.lambdaFunction;
import ai.vespa.schemals.parser.rankingexpression.ast.outs;
import ai.vespa.schemals.parser.rankingexpression.ast.scalarOrTensorFunction;
import ai.vespa.schemals.parser.rankingexpression.ast.tag;
import ai.vespa.schemals.parser.rankingexpression.ast.tensorReduceComposites;
import com.yahoo.searchlib.rankingexpression.evaluation.NamedStringValue;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;

import ai.vespa.schemals.schemadocument.resolvers.RankExpression.SpecificFunction;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * RankNode represents a node in the Rank expression AST.
 * The node can either represent an expression, a feature or a built-in-function
 * 
 * An expression is a rank expression.
 * A feature is a function with parameters, and an optional property. All parameters must be expressions.
 */
public class RankNode implements Iterable<RankNode>  {

    public static enum RankNodeType {
        FEATURE,
        EXPRESSION,
        BUILT_IN_FUNCTION,
        TAGGED_ARGUMENT
    };

    private SchemaNode schemaNode;
    private RankNodeType type;
    private boolean insideLambdaFunction = false;
    private boolean argumentListExists = false;

    // parameters for features, child nodes for expressions
    private List<RankNode> children;

    // For features, this represents the text after the .
    private Optional<SchemaNode> property;

    // For feature nodes, this specifies the function signature
    private Optional<SpecificFunction> functionSignature = Optional.empty();

    // For tagged arguments: field: value / label: value
    private Optional<String> tagKey = Optional.empty();
    private Optional<String> tagValue = Optional.empty();
    private Optional<SchemaNode> tagValueNode = Optional.empty();

    public static RankNode wrap(SchemaNode node) {
        return new RankNode(node);
    }

    private RankNode(SchemaNode node) {
        this.schemaNode = node;
        this.type = rankNodeTypeMap.get(node.getASTClass());

        node.setRankNode(this);

        if (this.type == RankNodeType.EXPRESSION) {

            this.children = findChildren(node);

        } else if (this.type == RankNodeType.FEATURE) {

            Optional<List<RankNode>> children = findParameters(node);
            if (children.isPresent()) {
                this.children = children.get();
                argumentListExists = true;
            } else {
                this.children = new ArrayList<>();
            }

            this.property = findProperty(node);
            if (this.property.isPresent()) {
                this.property.get().setRankNode(this);
            }

        } else if (this.type == RankNodeType.BUILT_IN_FUNCTION) {
            this.children = findBuiltInChildren(node);
        }

        if (node.isASTInstance(lambdaFunction.class)) {
            setInsideLambdaFunction();
        }
    }

    private static List<RankNode> findChildren(SchemaNode node) {
        List<RankNode> ret = new ArrayList<>();

        for (Node child : node) {
            if (rankNodeTypeMap.containsKey(child.getASTClass())) {
                ret.add(new RankNode(child.getSchemaNode()));
            } else {
                ret.addAll(findChildren(child.getSchemaNode()));
            }
        }

        return ret;
    }

    private static Optional<RankNode> tryParseTaggedArgument(SchemaNode argExprNode) {
        if (!(argExprNode.getOriginalRankExpressionNode() instanceof BaseNode baseNode)
                || !(baseNode.expressionNode instanceof ConstantNode constantNode)
                || !(constantNode.getValue() instanceof NamedStringValue namedValue)) {
            return Optional.empty();
        }
        SchemaNode valueNode = findTagValueNode(argExprNode).orElse(argExprNode);
        return Optional.of(makeTaggedArgument(argExprNode, namedValue.name(), namedValue.value(), valueNode));
    }

    public static SchemaNode resolveTagValueLeaf(SchemaNode valueNode) {
        return findTagValueNode(valueNode).orElse(valueNode);
    }

    public static boolean tagValueIsIdentifier(SchemaNode valueNode) {
        SchemaNode leaf = resolveTagValueLeaf(valueNode);
        return leaf.isASTInstance(identifierStr.class) || leaf.isASTInstance(IDENTIFIER.class);
    }

    public static boolean tagValueIsString(SchemaNode valueNode) {
        return resolveTagValueLeaf(valueNode).isASTInstance(STRING.class);
    }

    public static boolean tagValueIsInteger(SchemaNode valueNode) {
        return resolveTagValueLeaf(valueNode).isASTInstance(INTEGER.class);
    }

    private static Optional<SchemaNode> findTagValueNode(SchemaNode node) {
        for (int i = node.size() - 1; i >= 0; i--) {
            Node child = node.get(i);
            if (child.isASTInstance(COLON.class)) {
                continue;
            }
            SchemaNode childNode = child.getSchemaNode();
            if (childNode.isASTInstance(identifierStr.class)
                    || childNode.isASTInstance(IDENTIFIER.class)
                    || childNode.isASTInstance(INTEGER.class)
                    || childNode.isASTInstance(STRING.class)
                    || childNode.isASTInstance(tag.class)
                    || childNode.isASTInstance(tagValueStr.class)) {
                Optional<SchemaNode> nested = findTagValueNode(childNode);
                if (nested.isPresent()) {
                    return nested;
                }
                return Optional.of(childNode);
            }
            Optional<SchemaNode> nested = findTagValueNode(childNode);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    private static RankNode makeTaggedArgument(SchemaNode argExprNode, String key, String value,
                                               SchemaNode valueNode) {
        RankNode rankNode = new RankNode(argExprNode);
        rankNode.type = RankNodeType.TAGGED_ARGUMENT;
        rankNode.children = new ArrayList<>();
        rankNode.tagKey = Optional.of(key);
        rankNode.tagValue = Optional.of(value);
        rankNode.tagValueNode = Optional.of(valueNode);
        return rankNode;
    }

    public boolean isTaggedArgument() {
        return tagKey.isPresent();
    }

    public String getTagKey() {
        return tagKey.orElse(null);
    }

    public String getTagValue() {
        return tagValue.orElse(null);
    }

    public SchemaNode getTagValueNode() {
        return tagValueNode.orElse(null);
    }

    private static Optional<List<RankNode>> findParameters(SchemaNode node) {
        Node parameterNode = null;

        for (int i = 0; i < node.size(); i++) {
            if (node.get(i).getASTClass() == args.class) {
                parameterNode = node.get(i);
                break;
            }
        }

        if (parameterNode == null) {
            return Optional.empty();
        }

        List<RankNode> ret = new ArrayList<>();

        for (Node child : parameterNode) {
            if (child.isASTInstance(ai.vespa.schemals.parser.rankingexpression.ast.COMMA.class)) {
                continue;
            }
            SchemaNode childNode = child.getSchemaNode();
            if (childNode.isASTInstance(argExpression.class)) {
                addParameterFromArgExpression(childNode, ret);
            } else if (child.getASTClass() == expression.class) {
                Optional<RankNode> tagged = findArgExpressionNode(childNode).flatMap(RankNode::tryParseTaggedArgument);
                if (tagged.isPresent()) {
                    ret.add(tagged.get());
                } else {
                    ret.add(new RankNode(childNode));
                }
            }
        }

        return Optional.of(ret);
    }

    private static void addParameterFromArgExpression(SchemaNode argExprNode, List<RankNode> ret) {
        Optional<RankNode> tagged = tryParseTaggedArgument(argExprNode);
        if (tagged.isPresent()) {
            ret.add(tagged.get());
            return;
        }
        for (Node grandchild : argExprNode) {
            if (grandchild.getASTClass() == expression.class) {
                ret.add(new RankNode(grandchild.getSchemaNode()));
            }
        }
    }

    private static Optional<SchemaNode> findArgExpressionNode(SchemaNode node) {
        if (node.isASTInstance(argExpression.class)) {
            return Optional.of(node);
        }
        for (Node child : node) {
            Optional<SchemaNode> found = findArgExpressionNode(child.getSchemaNode());
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static List<RankNode> findBuiltInChildren(SchemaNode node) {
        List<RankNode> ret = new ArrayList<>();

        for (Node child : node) {
            if (child.getASTClass() == expression.class) {
                ret.add(new RankNode(child.getSchemaNode()));
            }
        }

        return ret;
    }

    private static Optional<SchemaNode> findProperty(SchemaNode node) {
        Node propertyNode = null;

        for (int i = 0; i < node.size(); i++) {
            if (node.get(i).getASTClass() == outs.class) {
                propertyNode = node.get(i);
                break;
            }
        }

        if (propertyNode == null 
            || propertyNode.size() == 0 
            || propertyNode.getRange().getStart().equals(propertyNode.getRange().getEnd())) {
            return Optional.empty();
        }

        return Optional.of(propertyNode.get(0).getSchemaNode());

    }

    private static final Map<Class<?>, RankNodeType> rankNodeTypeMap = new HashMap<>() {{
        put(feature.class, RankNodeType.FEATURE);
        put(expression.class, RankNodeType.EXPRESSION);
        put(scalarOrTensorFunction.class, RankNodeType.BUILT_IN_FUNCTION);
        put(tensorReduceComposites.class, RankNodeType.BUILT_IN_FUNCTION);
        put(lambdaFunction.class, RankNodeType.BUILT_IN_FUNCTION);
    }};

    public static List<RankNode> createTree(SchemaNode node) {
        return findChildren(node);
    }

    public SchemaNode getSchemaNode() {
        return schemaNode;
    }

    public List<RankNode> getChildren() {
        return children;
    }

    public Optional<SchemaNode> getProperty() {
        return property;
    }

    public RankNodeType getType() {
        return type;
    }

    public Optional<SpecificFunction> getFunctionSignature() {
        return functionSignature;
    }

    public void setFunctionSignature(SpecificFunction signature) {
        functionSignature = Optional.of(signature);
    }

    public SchemaNode getSymbolNode() {
        if (type == RankNodeType.EXPRESSION  || schemaNode.size() == 0) {
            return null;
        }

        Node symbolNode = schemaNode.get(0);
        if (!symbolNode.hasSymbol()) {
            return null;
        }

        return symbolNode.getSchemaNode();
    }

    public boolean hasSymbol() {
        return (getSymbolNode() != null);
    }

    public Symbol getSymbol() {
        if (!hasSymbol()) return null;

        return getSymbolNode().getSymbol();
    }

    public SymbolType getSymbolType() {
        return getSymbolNode().getSymbol().getType();
    }

    public SymbolStatus getSymbolStatus() {
        return getSymbolNode().getSymbol().getStatus();
    }

    public Range getRange() {
        return schemaNode.getRange();
    }

    private void setInsideLambdaFunction() {
        if (insideLambdaFunction) return;

        insideLambdaFunction = true;
        for (RankNode child : children) {
            child.setInsideLambdaFunction();
        }
    }

    public boolean getInsideLambdaFunction() {
        return insideLambdaFunction;
    }

    public boolean getArgumentListExists() {
        return argumentListExists;
    }

    public String toString() {
        return "[RANK: " + type + "] " + schemaNode.toString() + (hasSymbol() ? " Symbol: " + getSymbol().toString() : "");
    }

    @Override
    public Iterator<RankNode> iterator() {
        return new Iterator<RankNode>() {
            int currentIndex = 0;

			@Override
			public boolean hasNext() {
                return currentIndex < children.size();
			}

			@Override
			public RankNode next() {
                return children.get(currentIndex++);
			}
        };
    }
}
