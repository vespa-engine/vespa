package ai.vespa.schemals.tree;

import ai.vespa.schemals.parser.Token.TokenType;

public class SymbolDefinitionNode extends SymbolNode {


    public SymbolDefinitionNode(SchemaNode node, TokenType symbolType) {
        super(node, symbolType);
    }
    
}
