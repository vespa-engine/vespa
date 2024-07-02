package ai.vespa.schemals;

import ai.vespa.schemals.completion.SchemaCompletion;
import ai.vespa.schemals.context.EventContextCreator;
import ai.vespa.schemals.context.SchemaDocumentScheduler;
import ai.vespa.schemals.context.SchemaIndex;
import ai.vespa.schemals.definition.SchemaDefinition;
import ai.vespa.schemals.hover.SchemaHover;
import ai.vespa.schemals.semantictokens.SchemaSemanticTokens;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
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
    private EventContextCreator eventContextCreator;

    public SchemaTextDocumentService(PrintStream logger, SchemaDocumentScheduler schemaDocumentScheduler, SchemaIndex schemaIndex) {
        this.logger = logger;
        eventContextCreator = new EventContextCreator(logger, schemaDocumentScheduler, schemaIndex);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams) {
        // Provide completion item.
        return CompletableFutures.computeAsync((canelChecker) -> {
            try {

                return Either.forLeft(SchemaCompletion.getCompletionItems(eventContextCreator.createContext(completionParams)));

            } catch(CancellationException ignore) {
                // Ignore
            } catch (Throwable e) {
                logger.println(e);
            }

            // Return the list of completion items.
            return Either.forLeft(new ArrayList<>());
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

        SchemaDocumentScheduler scheduler = eventContextCreator.scheduler;

        scheduler.openDocument(document);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var document = params.getTextDocument();

        SchemaDocumentScheduler scheduler = eventContextCreator.scheduler;

        var contentChanges = params.getContentChanges();
        for (int i = 0; i < contentChanges.size(); i++) {
            scheduler.updateFile(document.getUri(), contentChanges.get(i).getText());
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        TextDocumentIdentifier documentIdentifier = params.getTextDocument();
        SchemaDocumentScheduler scheduler = eventContextCreator.scheduler;

        scheduler.closeDocument(documentIdentifier.getUri());
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

                return SchemaSemanticTokens.getSemanticTokens(eventContextCreator.createContext(params));
                 
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

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFutures.computeAsync((cancelChecker) -> {

            try {

                return SchemaHover.getHover(eventContextCreator.createContext(params));

            } catch (CancellationException ignore) {
                // Ignore
            } catch (Throwable e) {
                logger.println(e);
            }

            return null; 
        });
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
    
            try {
    
                return Either.forLeft(SchemaDefinition.getDefinition(eventContextCreator.createContext(params)));
    
            } catch (CancellationException ignore) {
                // Ignore
            } catch (Throwable e) {
                logger.println(e);
            }
    
            return Either.forLeft(new ArrayList<Location>());
        });
    }
}
