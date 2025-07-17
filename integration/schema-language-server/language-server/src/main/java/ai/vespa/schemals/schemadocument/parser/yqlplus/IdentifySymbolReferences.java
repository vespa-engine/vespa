package ai.vespa.schemals.schemadocument.parser.yqlplus;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.yqlplus.ast.identifierStr;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.YQLNode;

public class IdentifySymbolReferences extends Identifier<YQLNode> {

    public IdentifySymbolReferences(ParseContext context) {
        super(context);
    }

    @Override
    public void identify(YQLNode node, List<Diagnostic> diagnostics) {
        if (node.getASTClass() == identifierStr.class || node.getASTClass() == ai.vespa.schemals.parser.grouping.ast.identifierStr.class) {
            context.logger().info("TODO");
        }
    }

}
