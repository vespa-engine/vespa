package ai.vespa.schemals.lsp.yqlplus.codelens;

import java.util.List;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.lsp.common.command.CommandRegistry;
import ai.vespa.schemals.lsp.common.command.CommandRegistry.CommandType;
import ai.vespa.schemals.schemadocument.DocumentManager.DocumentType;

public class YQLPlusCodeLens {


    static public List<CodeLens> codeLens(EventDocumentContext context) {

        if (context.document.getDocumentType() != DocumentType.YQL) {
            return List.of();
        }

        return List.of(new CodeLens(
            new Range(new Position(1, 1), new Position(1, 4)),
            CommandRegistry.createLSPCommand(CommandType.RUN_VESPA_QUERY, List.of()),
            null
        ));
    }
}
