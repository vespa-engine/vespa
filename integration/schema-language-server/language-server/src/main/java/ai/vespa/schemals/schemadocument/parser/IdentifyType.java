package ai.vespa.schemals.schemadocument.parser;

import java.util.Optional;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.schema.parser.ParsedType;
import com.yahoo.schema.parser.ParsedType.Variant;
import com.yahoo.search.schema.RankProfile.InputType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TensorTypeParser;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.FLOAT_KEYWORD;
import ai.vespa.schemals.parser.ast.LONG_KEYWORD;
import ai.vespa.schemals.parser.ast.annotationBody;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.inputElm;
import ai.vespa.schemals.parser.ast.valueType;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyType extends Identifier {
    public IdentifyType(ParseContext context) {
		super(context);
	}

    public List<Diagnostic> identify(SchemaNode node) {
        List<Diagnostic> ret = new ArrayList<Diagnostic>();

        if (node.isASTInstance(inputElm.class)) {
            return identifyInputType(node);
        }

        if (!node.isSchemaASTInstance(dataType.class)) {
            return ret;
        }

        dataType originalNode = (dataType)node.getOriginalSchemaNode();

        ParsedType parsedType = originalNode.getParsedType();

        if (originalNode.isArrayOldStyle) {
            String nodeText = node.get(0).getText();
            ret.add(new SchemaDiagnostic.Builder()
                    .setRange(node.getRange())
                    .setMessage("Data type syntax '" + nodeText + "[]' is deprecated, use 'array<" + nodeText + ">' instead.")
                    .setSeverity(DiagnosticSeverity.Warning)
                    .setCode(DiagnosticCode.DEPRECATED_ARRAY_SYNTAX)
                    .build());
        }

        if (parsedType == null) {
            // some parsing error has occured
            return ret;
        }

        if (parsedType.getVariant() == Variant.ANN_REFERENCE) {
            if (!isInsideAnnotationBody(node)) {
                ret.add(new SchemaDiagnostic.Builder()
                        .setRange( node.getRange())
                        .setMessage( "annotationreference should only be used inside an annotation")
                        .setSeverity( DiagnosticSeverity.Error)
                        .setCode(DiagnosticCode.ANNOTATION_REFERENCE_OUTSIDE_ANNOTATION)
                        .build() );
            }
        }

        if (parsedType.getVariant() == Variant.MAP) {
            SchemaNode keyTypeNode = node.get(0).get(2);

            if (keyTypeNode != null && keyTypeNode.isASTInstance(dataType.class)) {
                ParsedType keyParsedType = ((dataType)keyTypeNode.getOriginalSchemaNode()).getParsedType();
                if (keyParsedType.getVariant() != Variant.BUILTIN) {
                    // TODO: quickfix
                    ret.add(new SchemaDiagnostic.Builder()
                            .setRange( keyTypeNode.getRange())
                            .setMessage( "Map key type must be a primitive type")
                            .setSeverity( DiagnosticSeverity.Error)
                            .build() );
                }
            }
        }

        if (parsedType.getVariant() != Variant.UNKNOWN) {
            return ret;
        }

        Optional<Symbol> scope = CSTUtils.findScope(node);
        if (scope.isPresent()) {
            node.setSymbol(SymbolType.TYPE_UNKNOWN, context.fileURI(), scope.get());
        } else {
            node.setSymbol(SymbolType.TYPE_UNKNOWN, context.fileURI());
        }

        this.context.addUnresolvedTypeNode(node);
        return ret;
    }

    private static boolean isInsideAnnotationBody(SchemaNode node) {
        while (node != null) {
            if (node.isSchemaASTInstance(annotationBody.class)) return true;
            node = node.getParent();
        }
        return false;
    }

    private List<Diagnostic> identifyInputType(SchemaNode node) {
        List<Diagnostic> ret = new ArrayList<>();

        if (!node.isASTInstance(inputElm.class) || node.size() == 0) {
            return ret;
        }

        SchemaNode valueTypeNode = null;
        for (SchemaNode child : node) {
            if (child.isASTInstance(valueType.class)) {
                valueTypeNode = child;
                break;
            }
        }

        if (valueTypeNode == null) {
            return ret;
        }

        SchemaNode typeToken = valueTypeNode.findFirstLeaf();
        String identifierName = node.get(0).getText();
        String warningMessage = null;
        if (typeToken.isASTInstance(LONG_KEYWORD.class)) {
            warningMessage = "Input " + identifierName + ": 'long' is not possible, treated as 'double'";
        }

        if (typeToken.isASTInstance(FLOAT_KEYWORD.class)) {
            warningMessage = "Input " + identifierName + ": 'float' is not possible, treated as 'double'"; 
        }

        if (warningMessage != null) {
            ret.add(new SchemaDiagnostic.Builder()
                .setRange(valueTypeNode.getRange())
                .setMessage(warningMessage)
                .setSeverity(DiagnosticSeverity.Warning)
                .build());
        }

        return ret;
    }
}
