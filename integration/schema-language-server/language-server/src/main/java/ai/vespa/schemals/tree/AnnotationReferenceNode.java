package ai.vespa.schemals.tree;

import com.yahoo.schema.parser.ParsedType;

import ai.vespa.schemals.parser.ast.dataType;

public class AnnotationReferenceNode extends SchemaNode {

    public AnnotationReferenceNode(SchemaNode node) {
        super(node);
    }
}
