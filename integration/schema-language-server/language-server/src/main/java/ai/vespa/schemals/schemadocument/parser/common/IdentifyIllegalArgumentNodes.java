package ai.vespa.schemals.schemadocument.parser.common;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.Node.LanguageType;

public class IdentifyIllegalArgumentNodes extends Identifier<Node> {

    public IdentifyIllegalArgumentNodes(ParseContext context) {
        super(context);
    }

    @Override
    public void identify(Node node, List<Diagnostic> diagnostics) {
        if (node.getLanguageType() == LanguageType.SCHEMA
            && (node.getSchemaNode().getOriginalSchemaNode() instanceof ai.vespa.schemals.parser.Token))
        {
            ai.vespa.schemals.parser.Token nodeAsToken 
                = (ai.vespa.schemals.parser.Token)node.getSchemaNode().getOriginalSchemaNode();

            maybeAddToDiagnostics(
                diagnostics,
                node,
                nodeAsToken.getIllegalArgumentException()
            );
        }

        if (node.getLanguageType() == LanguageType.GROUPING) {
            if (node.getYQLNode().getOriginalGroupingNode() instanceof ai.vespa.schemals.parser.grouping.Token) {
                ai.vespa.schemals.parser.grouping.Token nodeAsGroupingToken 
                    = (ai.vespa.schemals.parser.grouping.Token)node.getYQLNode().getOriginalGroupingNode();

                maybeAddToDiagnostics(
                    diagnostics,
                    node,
                    nodeAsGroupingToken.getIllegalArgumentException()
                );
            }

            if (node.getYQLNode().getOriginalGroupingNode() instanceof ai.vespa.schemals.parser.grouping.ast.BaseNode) {
                ai.vespa.schemals.parser.grouping.ast.BaseNode nodeAsBaseNode
                    = (ai.vespa.schemals.parser.grouping.ast.BaseNode)node.getYQLNode().getOriginalGroupingNode();

                maybeAddToDiagnostics(
                    diagnostics,
                    node,
                    nodeAsBaseNode.getIllegalArgumentException()
                );
            }
        }
    }

    private void maybeAddToDiagnostics(List<Diagnostic> diagnostics, Node node, Exception ex) {
        if (ex == null) return;

        diagnostics.add(new SchemaDiagnostic.Builder()
            .setRange(node.getRange())
            .setMessage(ex.getMessage())
            .setSeverity(DiagnosticSeverity.Error)
            .build()
        );
    }
}
