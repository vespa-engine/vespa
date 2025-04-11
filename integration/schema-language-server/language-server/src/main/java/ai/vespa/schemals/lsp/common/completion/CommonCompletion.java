package ai.vespa.schemals.lsp.common.completion;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.schema.completion.SchemaCompletion;
import ai.vespa.schemals.lsp.yqlplus.completion.YQLCompletion;
import ai.vespa.schemals.schemadocument.DocumentManager.DocumentType;

/**
 * Responsible for LSP textDocument/completion requests.
 */
public class CommonCompletion {

    public static ArrayList<CompletionItem> getCompletionItems(EventCompletionContext context, PrintStream errorLogger) {

        if (StringUtils.isInsideComment(context.document.getCurrentContent(), context.position)) {
            return new ArrayList<CompletionItem>();
        }

        if (context.document.getDocumentType() == DocumentType.SCHEMA || context.document.getDocumentType() == DocumentType.PROFILE) {
            return SchemaCompletion.getCompletionItems(context, errorLogger);
        }

        return YQLCompletion.getCompletionItems(context, errorLogger);
    }
}
