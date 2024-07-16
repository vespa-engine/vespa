package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.annotationElm;
import ai.vespa.schemals.parser.ast.annotationOutside;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.documentSummary;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.fieldSetElm;
import ai.vespa.schemals.parser.ast.functionElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.mapDataType;
import ai.vespa.schemals.parser.ast.namedDocument;
import ai.vespa.schemals.parser.ast.rankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.structDefinitionElm;
import ai.vespa.schemals.parser.ast.structFieldDefinition;
import ai.vespa.schemals.parser.ast.summaryInDocument;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifySymbolDefinition extends Identifier {

    public IdentifySymbolDefinition(ParseContext context) {
		super(context);
	}

    private static final HashMap<Class<? extends Node>, SymbolType> identifierTypeMap = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(annotationElm.class, SymbolType.ANNOTATION);
        put(annotationOutside.class, SymbolType.ANNOTATION);
        put(rootSchema.class, SymbolType.SCHEMA);
        put(documentElm.class, SymbolType.DOCUMENT);
        put(namedDocument.class, SymbolType.DOCUMENT);
        put(fieldElm.class, SymbolType.FIELD);
        put(fieldSetElm.class, SymbolType.FIELDSET);
        put(structDefinitionElm.class, SymbolType.STRUCT);
        put(structFieldDefinition.class, SymbolType.FIELD);
        put(functionElm.class, SymbolType.FUNCTION);
    }};

    private static final HashMap<Class<? extends Node>, SymbolType> identifierWithDashTypeMap = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(rankProfile.class, SymbolType.RANK_PROFILE);
        put(documentSummary.class, SymbolType.DOCUMENT_SUMMARY);
        put(summaryInDocument.class, SymbolType.SUMMARY);
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        if (node.isASTInstance(dataType.class)) {
            handleDataTypeDefinition(node, ret);
            return ret;
        }
        
        boolean isIdentifier = node.isSchemaASTInstance(identifierStr.class);
        boolean isIdentifierWithDash = node.isSchemaASTInstance(identifierWithDashStr.class);

        if (!isIdentifier && !isIdentifierWithDash) return ret;

        SchemaNode parent = node.getParent();
        if (parent == null) return ret;

        // Prevent inheritance from beeing marked as a definition
        if (parent.indexOf(node) >= 3) {
            // Unnless it is a paramenter to a function
            if (parent.isASTInstance(functionElm.class) && node.isASTInstance(identifierStr.class)) {
                createSymbol(node, SymbolType.PARAMETER);
            }

            return ret;
        }

        Map<Class<? extends Node>, SymbolType> searchMap = isIdentifier ? identifierTypeMap : identifierWithDashTypeMap;
        SymbolType symbolType = searchMap.get(parent.getASTClass());
        if (symbolType != null) {

            Optional<Symbol> scope = findScope(node);
            if (scope.isEmpty()) return ret;

            node.setSymbol(symbolType, context.fileURI(), scope.get());

            if (context.schemaIndex().findSymbol(node.getSymbol()).isEmpty()) {
                node.setSymbolStatus(SymbolStatus.DEFINITION);
                context.schemaIndex().insertSymbolDefinition(node.getSymbol());
            } else {
                node.setSymbolStatus(SymbolStatus.INVALID);
            }

            return ret;
        }

        // if (parent.isASTInstance(structFieldDefinition.class) && parent.getParent() != null) {
        //     // Custom logic to find the scope

        //     SchemaNode parentDefinitionNode = parent.getParent().get(1);

        //     if (parentDefinitionNode.hasSymbol() && parentDefinitionNode.getSymbol().getType() == SymbolType.STRUCT) {
        //         Symbol scope = parentDefinitionNode.getSymbol();
        //         node.setSymbol(SymbolType.FIELD_IN_STRUCT, context.fileURI(), scope);
        //         node.setSymbolStatus(SymbolStatus.DEFINITION);
        //         context.schemaIndex().insertSymbolDefinition(node.getSymbol());
        //     } else {
        //         ret.add(new Diagnostic(node.getRange(), "Invalid field definition in struct", DiagnosticSeverity.Warning, ""));
        //     }
        // }

        // // TODO: these cases are quite similar. Generalize?
        // if (parent.isASTInstance(rankProfile.class)) {
        //     Optional<Symbol> scope = findRankProfileScope(node, context.fileURI());

        //     if (scope.isPresent()) {
        //         node.setSymbol(SymbolType.RANK_PROFILE, context.fileURI(), scope.get());

        //         if (context.schemaIndex().findSymbol(node.getSymbol()).isEmpty()) {
        //             node.setSymbolStatus(SymbolStatus.DEFINITION);
        //             context.schemaIndex().insertSymbolDefinition(node.getSymbol());
        //         }
        //     } else {
        //         node.setSymbol(SymbolType.RANK_PROFILE, context.fileURI());
        //         node.setSymbolStatus(SymbolStatus.INVALID);
        //     }
        // }

        // if (parent.isASTInstance(functionElm.class)) {
        //     Optional<Symbol> scope = findRankProfileFunctionScope(node);
        //     if (scope.isPresent()) {
        //         node.setSymbol(SymbolType.FUNCTION, context.fileURI(), scope.get());

        //         if (context.schemaIndex().findSymbol(node.getSymbol()) == null) {
        //             node.setSymbolStatus(SymbolStatus.DEFINITION);
        //             context.schemaIndex().insertSymbolDefinition(node.getSymbol());
        //         }
        //     } else {
        //         node.setSymbol(SymbolType.FUNCTION, context.fileURI());
        //         node.setSymbolStatus(SymbolStatus.INVALID);
        //     }
        // }


        return ret;
    }

    /**
     * Some datatypes need to define symbols.
     * Currently only map, which defines MAP_KEY and MAP_VALUE symbols at the dataType nodes inside the map
     */
    private void handleDataTypeDefinition(SchemaNode node, List<Diagnostic> diagnostics) {
        if (node.getParent() == null || !node.getParent().isASTInstance(mapDataType.class)) return;

        Optional<Symbol> scope = findMapScope(node.getParent());

        if (!scope.isPresent()) return;

        if (node.getParent().indexOf(node) == 2) {
            // Map key type
            node.setSymbol(SymbolType.MAP_KEY, context.fileURI(), scope.get(), "key");
            node.setSymbolStatus(SymbolStatus.DEFINITION);
            context.schemaIndex().insertSymbolDefinition(node.getSymbol());
        } else if (node.getParent().indexOf(node) == 4) {
            // Map value type
            // Should only define a new type if this guy is not a reference to something else
            dataType dataTypeNode = (dataType)node.getOriginalSchemaNode();
            if (dataTypeNode.getParsedType().getVariant() == Variant.UNKNOWN) return;

            node.setSymbol(SymbolType.MAP_VALUE, context.fileURI(), scope.get(), "value");
            node.setSymbolStatus(SymbolStatus.DEFINITION);
            context.schemaIndex().insertSymbolDefinition(node.getSymbol());
        }
    }

    private Optional<Symbol> findMapScope(SchemaNode mapDataTypeNode) {
        while (mapDataTypeNode != null) {
            mapDataTypeNode = mapDataTypeNode.getParent();
            if (mapDataTypeNode == null) return Optional.empty();

            if (mapDataTypeNode.hasSymbol()) {
                return Optional.of(mapDataTypeNode.getSymbol());
            }

            if (mapDataTypeNode.isASTInstance(fieldElm.class) || mapDataTypeNode.isASTInstance(structFieldDefinition.class)) {
                SchemaNode fieldIdentifierNode = mapDataTypeNode.get(1);
                if (fieldIdentifierNode == null) return Optional.empty();
                if (!fieldIdentifierNode.hasSymbol() || fieldIdentifierNode.getSymbol().getStatus() != SymbolStatus.DEFINITION) return Optional.empty();
                return Optional.of(fieldIdentifierNode.getSymbol());
            }
        }
        return Optional.empty();
    }

    private void createSymbol(SchemaNode node, SymbolType type) {

        Optional<Symbol> scope = findScope(node);

        if (scope.isPresent()) {
            node.setSymbol(type, context.fileURI(), scope.get());
        } else {
            node.setSymbol(type, context.fileURI());
        }

        node.setSymbolStatus(SymbolStatus.DEFINITION);
        context.schemaIndex().insertSymbolDefinition(node.getSymbol());
    }

    private Optional<Symbol> findScope(SchemaNode node) {
        if (
            context.fileURI().toLowerCase().endsWith(".profile") &&
            node.getParent().isASTInstance(rankProfile.class)
        ) return findRankProfileScopeFromURI(context.fileURI());

        SchemaNode currentNode = node;

        while (
            currentNode != null
        ) {
            if (
                identifierTypeMap.keySet().contains(currentNode.getASTClass()) ||
                identifierWithDashTypeMap.keySet().contains(currentNode.getASTClass())
            ) {
                // Find the symbol definition
                // TODO: Recaftor in a more general way
                int indexGuess = 1;

                if (currentNode.isASTInstance(functionElm.class)) {
                    indexGuess = 2;
                }

                if (currentNode.size() >= indexGuess + 1) {
                    SchemaNode potentialDefinition = currentNode.get(indexGuess);
                    if (potentialDefinition.hasSymbol() && potentialDefinition.getSymbol().getStatus() == SymbolStatus.DEFINITION) {
                        return Optional.of(potentialDefinition.getSymbol());
                    }
                }
            }
        }

        return Optional.empty();
    }

    private Optional<Symbol> findRankProfileScopeFromURI(String fileURI) {
        SchemaDocument document = context.scheduler().getSchemaDocument(fileURI);
        String schemaName = document.getSchemaIdentifier();
        context.logger().println("Lookup for " + fileURI + " retuned " + schemaName);
        if (schemaName == null) return Optional.empty();
        return context.schemaIndex().getSchemaDefinition(schemaName);
    }
}
