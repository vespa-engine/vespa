package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.schema.parser.ParsedType;
import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.annotationBody;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyType extends Identifier {
    public IdentifyType(ParseContext context) {
		super(context);
	}

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        if (!node.isASTInstance(dataType.class)) {
            return ret;
        }

        dataType originalNode = (dataType)node.getOriginalNode();

        ParsedType parsedType = originalNode.getParsedType();

        if (originalNode.isArrayOldStyle) {
            String nodeText = node.get(0).getText();
            ret.add(new Diagnostic(
                node.getRange(), 
                "Data type syntax '" + nodeText + "[]' is deprecated, use 'array<" + nodeText + ">' instead.", 
                DiagnosticSeverity.Warning,""
            ));

        }

        if (parsedType.getVariant() == Variant.ANN_REFERENCE) {
            if (!isInsideAnnotationBody(node)) {
                ret.add(new Diagnostic(
                    node.getRange(),
                    "annotationreference should only be used inside an annotation",
                    DiagnosticSeverity.Error,
                    ""
                ));
            }
        }

        if (parsedType.getVariant() != Variant.UNKNOWN) {
            return ret;
        }

        node.setSymbol(SymbolType.TYPE_UNKNOWN, context.fileURI());

        this.context.addUnresolvedTypeNode(node);
        return ret;
    }

    private static boolean isInsideAnnotationBody(SchemaNode node) {
        while (node != null) {
            if (node.isASTInstance(annotationBody.class)) return true;
            node = node.getParent();
        }
        return false;
    }
}
