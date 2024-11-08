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

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.COMMA;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * InheritanceResolver resolves all inheritance, and registers them in inheritance graphs
 */
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
                        diagnostics.add(new SchemaDiagnostic.Builder()
                                .setRange( context.inheritsSchemaNode().getRange())
                                .setMessage( "The schema document must explicitly inherit from " + inheritsSchemaName + " because the containing schema does so.")
                                .setSeverity( DiagnosticSeverity.Error)
                                .setCode(DiagnosticCode.EXPLICITLY_INHERIT_DOCUMENT)
                                .build() );
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
        if (inheritanceNode.getPreviousSibling() != null && inheritanceNode.getPreviousSibling().getASTClass() == COMMA.class) {
            diagnostics.add(new SchemaDiagnostic.Builder()
                             .setRange(inheritanceNode.getRange())
                             .setMessage("Inheriting multiple structs is not supported.")
                             .setSeverity(DiagnosticSeverity.Error)
                             .build());
            return;
        }
        Node myStructDefinitionNode = inheritanceNode.getParent().getPreviousSibling();

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
            diagnostics.add(new SchemaDiagnostic.Builder()
                    .setRange( inheritanceNode.getRange())
                    .setMessage( "Cannot inherit from " + parentSymbol.get().getShortIdentifier() + " because " + parentSymbol.get().getShortIdentifier() + " inherits from this struct.")
                    .setSeverity( DiagnosticSeverity.Error)
                    .build() );
        }


        // Look for redeclarations

        Symbol structDefinitionSymbol = myStructDefinitionNode.getSymbol();

        Map<String, Symbol> inheritedFields = new HashMap<>();
        List<Symbol> myFields = new ArrayList<>();

        for (Symbol fieldSymbol : context.schemaIndex().listSymbolsInScope(structDefinitionSymbol, SymbolType.FIELD)) {
            if (fieldSymbol.getScope().equals(structDefinitionSymbol)) {
                myFields.add(fieldSymbol);
            } else {
                inheritedFields.put(fieldSymbol.getShortIdentifier(), fieldSymbol);
            }
        }

        for (Symbol fieldSymbol : myFields) {
            if (!inheritedFields.containsKey(fieldSymbol.getShortIdentifier())) continue;
            diagnostics.add(new SchemaDiagnostic.Builder()
                    .setRange(fieldSymbol.getNode().getRange())
                    .setMessage("struct " + myStructDefinitionNode.getText() + " cannot inherit from " + parentSymbol.get().getShortIdentifier() + " and redeclare field " + fieldSymbol.getShortIdentifier())
                    .setSeverity(DiagnosticSeverity.Error)
                    .setCode(DiagnosticCode.INHERITS_STRUCT_FIELD_REDECLARED)
                    .build());
        }
    }

    private static void resolveRankProfileInheritance(SchemaNode inheritanceNode, ParseContext context, List<Diagnostic> diagnostics) {
        Node myRankProfileDefinitionNode = inheritanceNode.getParent().getPreviousSibling();
        String inheritedIdentifier = inheritanceNode.getText();

        if (myRankProfileDefinitionNode == null) return;
        if (!myRankProfileDefinitionNode.hasSymbol() || myRankProfileDefinitionNode.getSymbol().getStatus() != SymbolStatus.DEFINITION) return;

        List<Symbol> parentSymbols = context.schemaIndex().findSymbols(inheritanceNode.getSymbol());

        if (parentSymbols.isEmpty() || 
            // prevents rank-profile default inherits default from causing cyclic inheritance
            (inheritedIdentifier.equals("default") && myRankProfileDefinitionNode.getSymbol().getShortIdentifier().equals("default"))
        ) {
            if (inheritedIdentifier.equals("default")) {
                inheritanceNode.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
                return;
            }
            // Otherwise handled in resolve symbol ref
            return;
        }

        if (parentSymbols.size() > 1 && !parentSymbols.get(0).fileURIEquals(context.fileURI())) {
            String note = "\nNote:";

            for (Symbol symbol : parentSymbols) {
                note += "\nDefined in " + FileUtils.fileNameFromPath(symbol.getFileURI());
            }

            diagnostics.add(new SchemaDiagnostic.Builder()
                    .setRange( inheritanceNode.getRange())
                    .setMessage( inheritedIdentifier + " is ambiguous in this context. " + note)
                    .setSeverity( DiagnosticSeverity.Warning)
                    .build());
        }

        // Choose last one, if more than one (undefined behaviour if ambiguous).
        Symbol parentSymbol = parentSymbols.get(0);
        Symbol definitionSymbol = myRankProfileDefinitionNode.getSymbol();

        if (!checkRankProfileInheritanceCollisions(context, definitionSymbol, parentSymbol, inheritanceNode, diagnostics)) return;

        if (!context.schemaIndex().tryRegisterRankProfileInheritance(definitionSymbol, parentSymbol)) {
            diagnostics.add(new SchemaDiagnostic.Builder()
                    .setRange(inheritanceNode.getRange())
                    .setMessage("Cannot inherit from " + parentSymbol.getShortIdentifier() + " because " + parentSymbol.getShortIdentifier() + " inherits from this rank profile. ")
                    .setSeverity(DiagnosticSeverity.Error)
                    .build());

            return;
        }
    }

    private static boolean checkRankProfileInheritanceCollisions(ParseContext context, Symbol rankProfileDefinitionSymbol, Symbol inheritanceCandidate, SchemaNode inheritanceNode, List<Diagnostic> diagnostics) {
        Map<String, Symbol> functionDefinitionsBeforeInheritance = new HashMap<>();
        for (Symbol symbol : context.schemaIndex().listSymbolsInScope(rankProfileDefinitionSymbol, SymbolType.FUNCTION)) {
            // Skip shadowing inside the current rank-profile
            if (symbol.getScope().equals(rankProfileDefinitionSymbol)) continue;

            functionDefinitionsBeforeInheritance.put(symbol.getShortIdentifier(), symbol.getScope());
        }

        List<Symbol> newInheritedFunctions = context.schemaIndex().listSymbolsInScope(inheritanceCandidate, SymbolType.FUNCTION);

        boolean success = true;
        for (Symbol parentFunction : newInheritedFunctions) {
            if (functionDefinitionsBeforeInheritance.containsKey(parentFunction.getShortIdentifier())) {
                diagnostics.add(new SchemaDiagnostic.Builder()
                    .setRange(inheritanceNode.getRange())
                    .setMessage(
                        "Cannot inherit from " + inheritanceNode.getText() + " because " + inheritanceNode.getText() + 
                        " defines function " + parentFunction.getShortIdentifier() + " which is already defined in " + 
                        functionDefinitionsBeforeInheritance.get(parentFunction.getShortIdentifier()).getShortIdentifier()
                    )
                    .setSeverity(DiagnosticSeverity.Error)
                    .build()
                );
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
            diagnostics.add(new SchemaDiagnostic.Builder()
                    .setRange( inheritanceNode.getRange())
                    .setMessage( "Cannot inherit from " + schemaDocumentName + " because " + schemaDocumentName + " inherits from this document.")
                    .setSeverity( DiagnosticSeverity.Error)
                    .build() );
            return Optional.empty();
        }

        inheritanceNode.setSymbolStatus(SymbolStatus.REFERENCE);
        context.schemaIndex().insertSymbolReference(symbol.get(), inheritanceNode.getSymbol());
        return Optional.of(symbol.get().getFileURI());
    }

    private static void resolveDocumentSummaryInheritance(SchemaNode inheritanceNode, ParseContext context, List<Diagnostic> diagnostics) {
        Node myDocSummaryDefinitionNode = inheritanceNode.getParent().getPreviousSibling();

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
            diagnostics.add(new SchemaDiagnostic.Builder()
                    .setRange( inheritanceNode.getRange())
                    .setMessage( "Cannot inherit from " + parentSymbol.get().getShortIdentifier() + " because " + parentSymbol.get().getShortIdentifier() + " inherits from this document summary.")
                    .setSeverity( DiagnosticSeverity.Error)
                    .build() );
        }
    }
}
