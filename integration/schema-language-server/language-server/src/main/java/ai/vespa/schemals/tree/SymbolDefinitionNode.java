package ai.vespa.schemals.tree;

import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.identifierStr;

public class SymbolDefinitionNode extends SymbolNode {
    public SymbolDefinitionNode(SchemaNode node, TokenType symbolType) {
        super(node, symbolType);
    }
}
