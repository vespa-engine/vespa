package ai.vespa.schemals.schemadocument.resolvers;

import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.parser.ast.consumedFeatureListElm;
import ai.vespa.schemals.parser.ast.sortFeaturesElm;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * Identifier-gate for v1 {@code sort-features} list entries.
 * Matches {@code RankProfile.validateSortFeature}: each top-level entry must be a
 * bare schema identifier, with no arguments and no output selector.
 */
public class ValidateSortFeatures {
    public static final Pattern SCHEMA_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public static final String IDENTIFIER_ERROR_PREFIX =
        "sort-features entries must be bare schema identifiers ([A-Za-z_][A-Za-z0-9_]*)";

    public static boolean isSortFeatureList(Node node) {
        return node.getParent() != null
            && node.getParent().isASTInstance(consumedFeatureListElm.class)
            && CSTUtils.findASTClassAncestor(node, sortFeaturesElm.class) != null;
    }

    public static void validateReference(RankNode node, List<Diagnostic> diagnostics) {
        String text = node.getSchemaNode().getText();
        if (text == null) {
            text = "";
        }
        if (!node.getArgumentListExists() && node.getProperty().isEmpty()
                && SCHEMA_IDENTIFIER.matcher(text.trim()).matches()) {
            return;
        }
        diagnostics.add(new SchemaDiagnostic.Builder()
            .setRange(node.getRange())
            .setMessage(IDENTIFIER_ERROR_PREFIX + ", got '" + text + "'")
            .setSeverity(DiagnosticSeverity.Error)
            .build());
    }
}
