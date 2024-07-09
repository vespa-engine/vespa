package ai.vespa.schemals.context.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.ast.annotationRefDataType;
import ai.vespa.schemals.tree.AnnotationReferenceNode;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyAnnotationReference extends Identifier {

	public IdentifyAnnotationReference(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isASTInstance(annotationRefDataType.class)) return ret;

        if (node.size() < 3) return ret;

        SchemaNode annotationIdentifier = node.get(2);

        AnnotationReferenceNode replacedNode = new AnnotationReferenceNode(annotationIdentifier);
        context.addUnresolvedAnnotationReferenceNode(replacedNode);
        return ret;
	}
}
