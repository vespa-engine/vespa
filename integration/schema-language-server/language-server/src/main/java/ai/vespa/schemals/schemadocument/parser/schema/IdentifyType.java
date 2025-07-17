package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.schema.parser.ParsedType;
import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.FLOAT_KEYWORD;
import ai.vespa.schemals.parser.ast.LONG_KEYWORD;
import ai.vespa.schemals.parser.ast.annotationBody;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.inputElm;
import ai.vespa.schemals.parser.ast.valueType;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * IdentifyType identifies types in the schema document
 */
public class IdentifyType extends Identifier<SchemaNode> {
    public IdentifyType(ParseContext context) {
		super(context);
	}

    @Override
    public void identify(SchemaNode node, List<Diagnostic> diagnostics) {
        if (node.isASTInstance(inputElm.class)) {
            identifyInputType(node, diagnostics);
            return;
        }

        if (!node.isASTInstance(dataType.class)) {
            return;
        }

        dataType originalNode = (dataType)node.getOriginalSchemaNode();

        ParsedType parsedType = originalNode.getParsedType();

        if (originalNode.isArrayOldStyle) {
            String nodeText = node.get(0).getText();
            diagnostics.add(new SchemaDiagnostic.Builder()
                    .setRange(node.getRange())
                    .setMessage("Data type syntax '" + nodeText + "[]' is deprecated, use 'array<" + nodeText + ">' instead.")
                    .setSeverity(DiagnosticSeverity.Warning)
                    .setCode(DiagnosticCode.DEPRECATED_ARRAY_SYNTAX)
                    .build());
        }

        if (parsedType == null) {
            // some parsing error has occured
            return;
        }

        if (parsedType.getVariant() == Variant.ANN_REFERENCE) {
            if (!isInsideAnnotationBody(node)) {
                diagnostics.add(new SchemaDiagnostic.Builder()
                        .setRange( node.getRange())
                        .setMessage( "annotationreference should only be used inside an annotation")
                        .setSeverity( DiagnosticSeverity.Error)
                        .setCode(DiagnosticCode.ANNOTATION_REFERENCE_OUTSIDE_ANNOTATION)
                        .build() );
            }
        }

        if (parsedType.getVariant() == Variant.MAP) {
            Node keyTypeNode = node.get(0).get(2);

            if (keyTypeNode != null && keyTypeNode.isASTInstance(dataType.class)) {
                ParsedType keyParsedType = ((dataType)keyTypeNode.getSchemaNode().getOriginalSchemaNode()).getParsedType();
                if (keyParsedType.getVariant() != Variant.BUILTIN) {
                    // TODO: quickfix
                    diagnostics.add(new SchemaDiagnostic.Builder()
                            .setRange( keyTypeNode.getRange())
                            .setMessage( "Map key type must be a primitive type")
                            .setSeverity( DiagnosticSeverity.Error)
                            .build() );
                }
            }
        }

        if (parsedType.getVariant() != Variant.UNKNOWN) {
            return;
        }

        Optional<Symbol> scope = CSTUtils.findScope(node);
        if (scope.isPresent()) {
            node.setSymbol(SymbolType.TYPE_UNKNOWN, context.fileURI(), scope.get());
        } else {
            node.setSymbol(SymbolType.TYPE_UNKNOWN, context.fileURI());
        }

        this.context.addUnresolvedTypeNode(node);
    }

    private static boolean isInsideAnnotationBody(SchemaNode node) {
        Node currentNode = node;
        while (currentNode != null) {
            if (currentNode.isASTInstance(annotationBody.class)) return true;
            currentNode = currentNode.getParent();
        }
        return false;
    }

    private void identifyInputType(SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.isASTInstance(inputElm.class) || node.size() == 0) {
            return;
        }

        SchemaNode valueTypeNode = null;
        for (Node child : node) {
            if (child.isASTInstance(valueType.class)) {
                valueTypeNode = child.getSchemaNode();
                break;
            }
        }

        if (valueTypeNode == null) {
            return;
        }

        Node typeToken = valueTypeNode.findFirstLeaf();
        String identifierName = node.get(0).getText();
        String warningMessage = null;
        if (typeToken.isASTInstance(LONG_KEYWORD.class)) {
            warningMessage = "Input " + identifierName + ": 'long' is not possible, treated as 'double'";
        }

        if (typeToken.isASTInstance(FLOAT_KEYWORD.class)) {
            warningMessage = "Input " + identifierName + ": 'float' is not possible, treated as 'double'"; 
        }

        if (warningMessage != null) {
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(valueTypeNode.getRange())
                .setMessage(warningMessage)
                .setSeverity(DiagnosticSeverity.Warning)
                .build());
        }
    }
}
