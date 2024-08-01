package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.fieldOutsideDoc;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.structFieldElm;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyDeprecatedToken extends Identifier {
    public IdentifyDeprecatedToken(ParseContext context) {
		super(context);
	}

    private record DeprecatedToken(String message, DiagnosticCode code) {}

	private static final HashMap<TokenType, DeprecatedToken> deprecatedTokens = new HashMap<>() {{
        put(TokenType.ENABLE_BIT_VECTORS, new DeprecatedToken("",                    DiagnosticCode.DEPRECATED_TOKEN_ENABLE_BIT_VECTORS));
        put(TokenType.SUMMARY_TO,         new DeprecatedToken("",                    DiagnosticCode.DEPRECATED_TOKEN_SUMMARY_TO));
        put(TokenType.SEARCH,             new DeprecatedToken("Use schema instead.", DiagnosticCode.DEPRECATED_TOKEN_SEARCH));
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        // TODO: semantic context
        ArrayList<Diagnostic> ret = new ArrayList<>();

        DeprecatedToken entry = deprecatedTokens.get(node.getSchemaType());
        if (entry != null) {
            ret.add(
                new SchemaDiagnostic.Builder()
                    .setRange(node.getRange())
                    .setMessage(node.getText() + " is deprecated. " + entry.message)
                    .setSeverity(DiagnosticSeverity.Warning)
                    .setCode(entry.code)
                    .build()
            );

            return ret;
        }

        if ((node.getSchemaType() == TokenType.ATTRIBUTE || node.getSchemaType() == TokenType.INDEX) && node.getNextSibling() != null && node.getNextSibling().isASTInstance(identifierStr.class)) {
            String offendingKind;
            DiagnosticCode code;

            if (node.getSchemaType() == TokenType.ATTRIBUTE) {
                offendingKind = "attribute";
                code = DiagnosticCode.DEPRECATED_TOKEN_ATTRIBUTE;
            } else {
                offendingKind = "index";
                code = DiagnosticCode.DEPRECATED_TOKEN_INDEX;
            }

            String attributeName = node.getNextSibling().getText();
            SchemaNode fieldNode = node;

            while (fieldNode != null && !fieldNode.isASTInstance(structFieldElm.class) && !fieldNode.isASTInstance(fieldElm.class)) {
                fieldNode = fieldNode.getParent();
            }

            if (fieldNode != null && !fieldNode.get(1).getText().equals(attributeName)) {
                String fieldIdentifier = fieldNode.get(1).getText();

                if (fieldNode.getParent().isASTInstance(fieldOutsideDoc.class)) {
                    ret.add(
                        new SchemaDiagnostic.Builder()
                            .setRange(node.getNextSibling().getRange())
                            .setMessage("Cannot create " + offendingKind + " '" + attributeName + "' in field '" + fieldIdentifier + "' outside document.")
                            .setSeverity(DiagnosticSeverity.Error)
                            .build()
                    );
                } else {
                    ret.add(
                        new SchemaDiagnostic.Builder()
                            .setRange(node.getNextSibling().getRange())
                            .setMessage("Creating an " + offendingKind + " for field '" + fieldIdentifier + "' with a different name '" + attributeName + 
                                "' than the field name is deprecated, and support will be removed in Vespa 9. "
                              + " Create a field with the wanted name outside the document instead.")
                            .setSeverity(DiagnosticSeverity.Warning)
                            .setCode(code)
                            .build()
                    );
                }
            }
        }

        return ret;
    }
}
