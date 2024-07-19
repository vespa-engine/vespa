package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.google.protobuf.Option;

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.structFieldDefinition;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.SchemaNode;

public class InheritanceResolver {
    

    /**
     * Assuming first parsing step is done, use the list of unresolved inheritance
     * declarations to register the inheritance at index.
     * @return List of diagnostic found during inheritance handling
     */
    public static List<Diagnostic> resolveInheritances(ParseContext context) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Set<String> documentInheritanceURIs = new HashSet<>();

        for (SchemaNode inheritanceNode : context.unresolvedInheritanceNodes()) {
            if (inheritanceNode.getSymbol().getType() == SymbolType.DOCUMENT) {
                resolveDocumentInheritance(inheritanceNode, context, diagnostics).ifPresent(
                    parentURI -> documentInheritanceURIs.add(parentURI)
                );
            }
        }

        for (SchemaNode inheritanceNode : context.unresolvedInheritanceNodes()) {
            if (inheritanceNode.getSymbol().getType() == SymbolType.STRUCT) {
                resolveStructInheritance(inheritanceNode, context, diagnostics);
            }

            if (inheritanceNode.getSymbol().getType() == SymbolType.RANK_PROFILE) {
                resolveRankProfileInheritance(inheritanceNode, context, diagnostics);
            }

            if (inheritanceNode.getSymbol().getType() == SymbolType.DOCUMENT_SUMMARY) {
                resolveDocumentSummaryInheritance(inheritanceNode, context, diagnostics);
            }
        }

        if (context.inheritsSchemaNode() != null) {
            String inheritsSchemaName = context.inheritsSchemaNode().getText();

            Optional<Symbol> schemaSymbol = context.schemaIndex().getSchemaDefinition(inheritsSchemaName);

            if (schemaSymbol.isPresent()) {
                SchemaDocument parent = context.scheduler().getSchemaDocument(schemaSymbol.get().getFileURI());

                if (parent != null) {
                    if (!documentInheritanceURIs.contains(parent.getFileURI())) {
                        // TODO: quickfix
                        diagnostics.add(new Diagnostic(
                            context.inheritsSchemaNode().getRange(),
                            "The schema document must explicitly inherit from " + inheritsSchemaName + " because the containing schema does so.",
                            DiagnosticSeverity.Error,
                            ""
                        ));
                        context.schemaIndex().getDocumentInheritanceGraph().addInherits(context.fileURI(), parent.getFileURI());
                    }

                    context.schemaIndex().insertSymbolReference(schemaSymbol.get(), context.inheritsSchemaNode().getSymbol());
                }
            }
        }

        context.clearUnresolvedInheritanceNodes();

        return diagnostics;
    }

    private static void resolveStructInheritance(SchemaNode inheritanceNode, ParseContext context, List<Diagnostic> diagnostics) {
        SchemaNode myStructDefinitionNode = inheritanceNode.getParent().getPreviousSibling();

        if (myStructDefinitionNode == null) {
            return;
        }

        if (!myStructDefinitionNode.hasSymbol()) {
            return;
        }

        Optional<Symbol> parentSymbol = context.schemaIndex().findSymbol(inheritanceNode.getSymbol());
        if (parentSymbol.isEmpty()) {
            // Handled elsewhere
            return;
        }

        if (!context.schemaIndex().tryRegisterStructInheritance(myStructDefinitionNode.getSymbol(), parentSymbol.get())) {
            // TODO: get the chain?
            diagnostics.add(new Diagnostic(
                inheritanceNode.getRange(), 
                "Cannot inherit from " + parentSymbol.get().getShortIdentifier() + " because " + parentSymbol.get().getShortIdentifier() + " inherits from this struct.",
                DiagnosticSeverity.Error, 
                ""
            ));
        }


        // Look for redeclarations

        Symbol structDefinitionSymbol = myStructDefinitionNode.getSymbol();

        Set<String> inheritedFields = new HashSet<>();
        List<Symbol> myFields = new ArrayList<>();

        for (Symbol fieldSymbol : context.schemaIndex().listSymbolsInScope(structDefinitionSymbol, SymbolType.FIELD)) {
            if (fieldSymbol.getScope().equals(structDefinitionSymbol)) {
                myFields.add(fieldSymbol);
            } else {
                inheritedFields.add(fieldSymbol.getShortIdentifier());
            }
        }

        for (Symbol fieldSymbol : myFields) {
            if (!inheritedFields.contains(fieldSymbol.getShortIdentifier())) continue;

            // TODO: quickfix
            diagnostics.add(new Diagnostic(
                fieldSymbol.getNode().getRange(),
                "struct " + myStructDefinitionNode.getText() + " cannot inherit from " + parentSymbol.get().getShortIdentifier() + " and redeclare field " + fieldSymbol.getShortIdentifier(),
                DiagnosticSeverity.Error,
                ""
            ));
        }
    }

    private static void resolveRankProfileInheritance(SchemaNode inheritanceNode, ParseContext context, List<Diagnostic> diagnostics) {
        SchemaNode myRankProfileDefinitionNode = inheritanceNode.getParent().getPreviousSibling();
        String inheritedIdentifier = inheritanceNode.getText();

        if (myRankProfileDefinitionNode == null) return;
        if (!myRankProfileDefinitionNode.hasSymbol() || myRankProfileDefinitionNode.getSymbol().getStatus() != SymbolStatus.DEFINITION) return;

        if (inheritedIdentifier.equals("default")) {
            // TODO: mechanism for inheriting default rank profile. 
            // Workaround now: 
            inheritanceNode.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
            return;
        }


        List<Symbol> parentSymbols = context.schemaIndex().findSymbols(inheritanceNode.getSymbol());

        if (parentSymbols.isEmpty()) {
            // Handled in resolve symbol ref
            return;
        }

        if (parentSymbols.size() > 1 && !parentSymbols.get(0).getFileURI().equals(context.fileURI())) {
            String note = "\nNote:";

            for (Symbol symbol : parentSymbols) {
                note += "\nDefined in " + FileUtils.fileNameFromPath(symbol.getFileURI());
            }

            diagnostics.add(new Diagnostic(
                inheritanceNode.getRange(),
                inheritedIdentifier + " is ambiguous in this context.",
                DiagnosticSeverity.Warning,
                note
            ));
        }

        // Choose last one, if more than one (undefined behaviour if ambiguous).
        Symbol parentSymbol = parentSymbols.get(0);
        Symbol definitionSymbol = myRankProfileDefinitionNode.getSymbol();

        if (!checkRankProfileInheritanceCollisions(context, definitionSymbol, parentSymbol, inheritanceNode, diagnostics)) return;

        if (!context.schemaIndex().tryRegisterRankProfileInheritance(definitionSymbol, parentSymbol)) {
            diagnostics.add(new Diagnostic(
                inheritanceNode.getRange(),
                "Cannot inherit from " + parentSymbol.getShortIdentifier() + " because " + parentSymbol.getShortIdentifier() + " inherits from this rank profile.", 
                DiagnosticSeverity.Error,
                ""
            ));

            return;
        }
    }

    private static boolean checkRankProfileInheritanceCollisions(ParseContext context, Symbol rankProfileDefinitionSymbol, Symbol inheritanceCandidate, SchemaNode inheritanceNode, List<Diagnostic> diagnostics) {
        Map<String, Symbol> functionDefinitionsBeforeInheritance = new HashMap<>();
        for (Symbol symbol : context.schemaIndex().listSymbolsInScope(rankProfileDefinitionSymbol, SymbolType.FUNCTION)) {
            if (symbol.getScope().equals(rankProfileDefinitionSymbol)) continue;

            functionDefinitionsBeforeInheritance.put(symbol.getShortIdentifier(), symbol.getScope());
        }

        List<Symbol> parentFunctions = context.schemaIndex().listSymbolsInScope(inheritanceCandidate, SymbolType.FUNCTION);

        boolean success = true;
        for (Symbol parentFunction : parentFunctions) {
            if (functionDefinitionsBeforeInheritance.containsKey(parentFunction.getShortIdentifier())) {
                // TODO: quickfix and show the chain of inheritance
                diagnostics.add(new Diagnostic(
                    inheritanceNode.getRange(),
                    "Cannot inherit from " + inheritanceNode.getText() + " because " + inheritanceNode.getText() + 
                    " defines function " + parentFunction.getShortIdentifier() + " which is already defined in " + 
                    functionDefinitionsBeforeInheritance.get(parentFunction.getShortIdentifier()).getShortIdentifier(),
                    DiagnosticSeverity.Error,
                    ""
                ));
                success = false;
            }
        }
        return success;
    }

    private static Optional<String> resolveDocumentInheritance(SchemaNode inheritanceNode, ParseContext context, List<Diagnostic> diagnostics) {
        String schemaDocumentName = inheritanceNode.getText();
        Optional<Symbol> symbol = context.schemaIndex().getSchemaDefinition(schemaDocumentName);

        if (symbol.isEmpty()) {
            // Handled in resolve symbol references
            return Optional.empty();
        }

        if (!context.schemaIndex().tryRegisterDocumentInheritance(context.fileURI(), symbol.get().getFileURI())) {
            // Inheritance cycle
            // TODO: quickfix, get the chain?
            diagnostics.add(new Diagnostic(
                inheritanceNode.getRange(),
                "Cannot inherit from " + schemaDocumentName + " because " + schemaDocumentName + " inherits from this document.",
                DiagnosticSeverity.Error,
                ""
            ));
            return Optional.empty();
        }

        inheritanceNode.setSymbolStatus(SymbolStatus.REFERENCE);
        context.schemaIndex().insertSymbolReference(symbol.get(), inheritanceNode.getSymbol());
        return Optional.of(symbol.get().getFileURI());
    }

    private static void resolveDocumentSummaryInheritance(SchemaNode inheritanceNode, ParseContext context, List<Diagnostic> diagnostics) {
        SchemaNode myDocSummaryDefinitionNode = inheritanceNode.getParent().getPreviousSibling();

        if (myDocSummaryDefinitionNode == null) {
            return;
        }

        if (!myDocSummaryDefinitionNode.hasSymbol()) {
            return;
        }

        Optional<Symbol> parentSymbol = context.schemaIndex().findSymbol(inheritanceNode.getSymbol());
        if (parentSymbol.isEmpty()) {
            // Handled elsewhere
            return;
        }

        if (!context.schemaIndex().tryRegisterDocumentSummaryInheritance(myDocSummaryDefinitionNode.getSymbol(), parentSymbol.get())) {
            // TODO: get the chain?
            diagnostics.add(new Diagnostic(
                inheritanceNode.getRange(), 
                "Cannot inherit from " + parentSymbol.get().getShortIdentifier() + " because " + parentSymbol.get().getShortIdentifier() + " inherits from this document summary.",
                DiagnosticSeverity.Error, 
                ""
            ));
        }
    }
}