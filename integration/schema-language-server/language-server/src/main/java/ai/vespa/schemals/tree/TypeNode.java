package ai.vespa.schemals.tree;

import com.yahoo.schema.parser.ParsedType;

import ai.vespa.schemals.parser.ast.dataType;

public class TypeNode extends SchemaNode {

    public TypeNode(SchemaNode node) {
        super(node);
    }

    public ParsedType getParsedType() {
        return ((dataType)originalNode).getParsedType();
    }

    public boolean isArrayOldStyle() {
        return ((dataType)originalNode).isArrayOldStyle;
    }
}
