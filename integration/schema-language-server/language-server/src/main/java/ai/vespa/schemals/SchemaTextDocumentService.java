package ai.vespa.schemals;


import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.context.SchemaDocumentScheduler;
import ai.vespa.schemals.semantictokens.SchemaSemanticTokens;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensDelta;
import org.eclipse.lsp4j.SemanticTokensDeltaParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

public class SchemaTextDocumentService implements TextDocumentService {

    private PrintStream logger;
    private SchemaDocumentScheduler schemaDocumentScheduler;

    public SchemaTextDocumentService(PrintStream logger, SchemaDocumentScheduler schemaDocumentScheduler) {
        this.logger = logger;
        this.schemaDocumentScheduler = schemaDocumentScheduler;
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams) {
        // Provide completion item.
        return CompletableFuture.supplyAsync(() -> {
            List<CompletionItem> completionItems = new ArrayList<>();
            try {
                // Sample Completion item for sayHello
                CompletionItem completionItem = new CompletionItem();
                // Define the text to be inserted in to the file if the completion item is selected.
                completionItem.setInsertText("schema test {\n\n}");
                // Set the label that shows when the completion drop down appears in the Editor.
                completionItem.setLabel("New Schema");
                // Set the completion kind. This is a snippet.
                // That means it replace character which trigger the completion and
                // replace it with what defined in inserted text.
                completionItem.setKind(CompletionItemKind.Snippet);
                // This will set the details for the snippet code which will help user to
                // understand what this completion item is.
                completionItem.setDetail("Creates a new Schema");

                // Add the sample completion item to the list.
                completionItems.add(completionItem);

                CompletionItem completionItem2 = new CompletionItem();
                // Define the text to be inserted in to the file if the completion item is selected.
                completionItem2.setInsertText("document {\n\n}");
                // Set the label that shows when the completion drop down appears in the Editor.
                completionItem2.setLabel("document");
                // Set the completion kind. This is a snippet.
                // That means it replace character which trigger the completion and
                // replace it with what defined in inserted text.
                completionItem2.setKind(CompletionItemKind.Snippet);
                // This will set the details for the snippet code which will help user to
                // understand what this completion item is.
                completionItem2.setDetail("Creates a empty document");

                // Add the sample completion item to the list.
                completionItems.add(completionItem2);
            } catch (Exception e) {
                //TODO: Handle the exception.
            }

            // Return the list of completion items.
            return Either.forLeft(completionItems);
        });
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem completionItem) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams referenceParams) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams codeLensParams) {
        return null;
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens codeLens) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams documentFormattingParams) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams documentRangeFormattingParams) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams documentOnTypeFormattingParams) {
        return null;
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams renameParams) {
        return null;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        TextDocumentItem document = params.getTextDocument();

        schemaDocumentScheduler.openDocument(document);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var document = params.getTextDocument();

        var contentChanges = params.getContentChanges();
        for (int i = 0; i < contentChanges.size(); i++) {
            schemaDocumentScheduler.updateFile(document.getUri(), contentChanges.get(i).getText());
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        TextDocumentIdentifier documentIdentifier = params.getTextDocument();
        schemaDocumentScheduler.closeDocument(documentIdentifier.getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams didSaveTextDocumentParams) {

    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {

        return CompletableFutures.computeAsync((cancelChecker) -> {
            return new ArrayList<DocumentHighlight>();
        });
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return CompletableFutures.computeAsync((cancelChecker) -> {

            try {
                String fileURI = params.getTextDocument().getUri();
                SchemaDocumentParser documnet = schemaDocumentScheduler.getDocument(fileURI);

                return SchemaSemanticTokens.getSemanticTokens(documnet, logger);
            } catch (CancellationException ignore) {
                // Ignore cancellation exception
            } catch (Throwable e) {
                logger.println(e);
            }

            return new SemanticTokens(new ArrayList<>());
        });
    }

    public CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> semanticTokensFullDelta(SemanticTokensDeltaParams params) {
        return null;
    }
}
