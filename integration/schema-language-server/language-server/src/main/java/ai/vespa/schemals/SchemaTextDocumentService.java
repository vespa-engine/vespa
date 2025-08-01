package ai.vespa.schemals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
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
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensDelta;
import org.eclipse.lsp4j.SemanticTokensDeltaParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.TextDocumentService;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.context.EventContextCreator;
import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.context.EventFormattingContext;
import ai.vespa.schemals.context.EventRangeFormattingContext;
import ai.vespa.schemals.context.InvalidContextException;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.lsp.common.completion.CommonCompletion;
import ai.vespa.schemals.lsp.schema.codeaction.SchemaCodeAction;
import ai.vespa.schemals.lsp.schema.definition.SchemaDefinition;
import ai.vespa.schemals.lsp.schema.documentsymbols.SchemaDocumentSymbols;
import ai.vespa.schemals.lsp.schema.formatting.SchemaFormatting;
import ai.vespa.schemals.lsp.schema.hover.SchemaHover;
import ai.vespa.schemals.lsp.schema.references.SchemaReferences;
import ai.vespa.schemals.lsp.schema.rename.SchemaPrepareRename;
import ai.vespa.schemals.lsp.schema.rename.SchemaRename;
import ai.vespa.schemals.lsp.schema.semantictokens.SchemaSemanticTokens;
import ai.vespa.schemals.lsp.yqlplus.codelens.YQLPlusCodeLens;
import ai.vespa.schemals.lsp.yqlplus.semantictokens.YQLPlusSemanticTokens;
import ai.vespa.schemals.schemadocument.DocumentManager.DocumentType;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

/**
 * SchemaTextDocumentService handles incomming requests from the client.
 */
public class SchemaTextDocumentService implements TextDocumentService {

    private ClientLogger logger;
    private EventContextCreator eventContextCreator;
    private SchemaMessageHandler schemaMessageHandler;

    public SchemaTextDocumentService(ClientLogger logger, SchemaDocumentScheduler schemaDocumentScheduler, SchemaIndex schemaIndex, SchemaMessageHandler schemaMessageHandler) {
        this.logger = logger;
        this.schemaMessageHandler = schemaMessageHandler;
        eventContextCreator = new EventContextCreator(schemaDocumentScheduler, schemaIndex, schemaMessageHandler);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams) {
        // Provide completion item.
        return CompletableFutures.computeAsync((cancelChecker) -> {
            try {

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PrintStream errorLogger = new PrintStream(outputStream);
                Either<List<CompletionItem>, CompletionList> result =
                    Either.forLeft(CommonCompletion.getCompletionItems(eventContextCreator.createContext(completionParams), errorLogger));

                if (outputStream.size() > 0) {
                    schemaMessageHandler.logMessage(MessageType.Error, 
                        "Completion failed with errors: " + outputStream.toString() 
                    );
                }
                return result;
            } catch(CancellationException ignore) {
                // Ignore
            } catch(InvalidContextException ignore) {
                // Ignore
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
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            try {
                return SchemaCodeAction.provideActions(eventContextCreator.createContext(params));
            } catch(InvalidContextException ignore) {
                // Ignore
            } catch(Exception e) {
                logger.error("Error during code action handling: " + e.getMessage());
            }

            return new ArrayList<>();
        });
    }

    @Override
    public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
        return CompletableFutures.computeAsync((cancelChecker) -> {

            return unresolved;
        });
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            try {
                return SchemaReferences.getReferences(eventContextCreator.createContext(params));
            } catch(InvalidContextException ignore) {
                // Ignore
            }
            return List.of();
        });
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            try {
                EventDocumentContext context = eventContextCreator.createContext(params);
                return SchemaDocumentSymbols.documentSymbols(context);
            } catch(InvalidContextException ignore) {
                // Ignore
            } catch(Exception e) {
                logger.error("Error during document symbol handling: " + e.getMessage());
            }
            return List.of();
        });
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams codeLensParams) {

        return CompletableFutures.computeAsync((cancelChecker) -> {
            try {
                EventDocumentContext context = eventContextCreator.createContext(codeLensParams);
                return YQLPlusCodeLens.codeLens(context);
            } catch(InvalidContextException ignore) {
                // Ignore
            } catch (Exception e) {
                logger.error("Error during code lens request: " + e.getMessage());
            }
            return List.of();
        });
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens codeLens) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            try {
                EventFormattingContext context = eventContextCreator.createContext(params);
                return SchemaFormatting.computeFormattingEdits(context);
            } catch (InvalidContextException ignore) {
            } catch (Exception e) {
                logger.error("Error during formatting request: " + e.getMessage());
            }
            return List.of();
        });
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
            try {
                EventRangeFormattingContext context = eventContextCreator.createContext(params);
                return SchemaFormatting.computeRangeFormattingEdits(context);
            } catch (InvalidContextException ignore) {
            } catch (Exception e) {
                logger.error("Error during range formatting request: " + e.getMessage());
            }
            return List.of();
        });
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams documentOnTypeFormattingParams) {
        return null;
    }

    @Override
    public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params) {

        return CompletableFutures.computeAsync((cancelChecker) -> {
            try {
                return SchemaPrepareRename.prepareRename(eventContextCreator.createContext(params));
            } catch(InvalidContextException ignore) {
                // Ignore
            }
            return null;
        });
	}

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {

        return CompletableFutures.computeAsync((cancelChecker) -> {
            try {
                return SchemaRename.rename(eventContextCreator.createContext(params), params.getNewName());
            } catch(InvalidContextException ignore) {
                // Ignore
            }
            return null;
        });
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
            String fileURI = FileUtils.decodeURL(document.getUri());
            try {
                scheduler.updateFile(fileURI, contentChanges.get(i).getText());
            } catch(Exception e) {
                logger.error("Updating file " + fileURI + " failed with error: " + ClientLogger.errorToString(e));
            }
        }
    }


    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        TextDocumentIdentifier documentIdentifier = params.getTextDocument();
        SchemaDocumentScheduler scheduler = eventContextCreator.scheduler;

        scheduler.closeDocument(FileUtils.decodeURL(documentIdentifier.getUri()));
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
                EventDocumentContext context = eventContextCreator.createContext(params);
                if (context.document.getDocumentType() == DocumentType.YQL) {
                    return YQLPlusSemanticTokens.getSemanticTokens(context);
                }
                return SchemaSemanticTokens.getSemanticTokens(context);
                 
            } catch (InvalidContextException ex) {
                // ignore
            } catch (CancellationException ignore) {
                // Ignore cancellation exception
            } catch (Exception e) {
                logger.error(e);
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

                return SchemaHover.getHover(eventContextCreator.createContext(params), SchemaLanguageServer.getDefaultDocumentationPath());

            } catch (InvalidContextException ex) {
                // ignore
            } catch (CancellationException ignore) {
                // Ignore
            } catch (Exception e) {
                logger.error(e);
            }

            return null; 
        });
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        return CompletableFutures.computeAsync((cancelChecker) -> {
    
            try {
    
                return Either.forLeft(SchemaDefinition.getDefinition(eventContextCreator.createContext(params)));
    
            } catch (InvalidContextException ex) {
                // ignore
            } catch (CancellationException ignore) {
                // Ignore
            } catch (Exception e) {
                logger.error(e);
            }
    
            return Either.forLeft(new ArrayList<Location>());
        });
    }
}
