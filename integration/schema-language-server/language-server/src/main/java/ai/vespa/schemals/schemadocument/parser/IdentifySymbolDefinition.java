package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.schema.Schema;
import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.annotationElm;
import ai.vespa.schemals.parser.ast.annotationOutside;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.documentElm;
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
import ai.vespa.schemals.parser.ast.structFieldElm;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.schemadocument.ParseContext;
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
    }};

    private static final HashMap<Class<? extends Node>, SymbolType> identifierWithDashTypeMap = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(rankProfile.class, SymbolType.RANK_PROFILE);
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        if (node.isASTInstance(dataType.class)) {
            handleDataTypeDefinition(node, ret);
            return ret;
        }

        boolean isIdentifier = node.isASTInstance(identifierStr.class);
        boolean isIdentifierWithDash = node.isASTInstance(identifierWithDashStr.class);

        if (!isIdentifier && !isIdentifierWithDash) return ret;

        SchemaNode parent = node.getParent();
        if (parent == null) return ret;

        // Prevent inheritance from beeing marked as a definition
        if (parent.indexOf(node) >= 3) return ret;

        HashMap<Class<? extends Node>, SymbolType> searchMap = isIdentifier ? identifierTypeMap : identifierWithDashTypeMap;
        SymbolType symbolType = searchMap.get(parent.getASTClass());
        if (symbolType != null) {

            node.setSymbol(symbolType, context.fileURI());

            if (context.schemaIndex().findSymbolInFile(context.fileURI(), symbolType, node.getText()) == null) {
                node.setSymbolStatus(SymbolStatus.DEFINITION);
                context.schemaIndex().insertSymbolDefinition(node.getSymbol());
            } else {
                node.setSymbolStatus(SymbolStatus.INVALID);
            }

            return ret;
        }

        if (parent.isASTInstance(structFieldDefinition.class) && parent.getParent() != null) {
            // Custom logic to find the scope

            SchemaNode parentDefinitionNode = parent.getParent().get(1);

            if (parentDefinitionNode.hasSymbol() && parentDefinitionNode.getSymbol().getType() == SymbolType.STRUCT) {
                Symbol scope = parentDefinitionNode.getSymbol();
                node.setSymbol(SymbolType.FIELD_IN_STRUCT, context.fileURI(), scope);
                node.setSymbolStatus(SymbolStatus.DEFINITION);
                context.schemaIndex().insertSymbolDefinition(node.getSymbol());
            } else {
                ret.add(new Diagnostic(node.getRange(), "Invalid field definition in struct", DiagnosticSeverity.Warning, ""));
            }
        }

        if (parent.isASTInstance(functionElm.class)) {
            Optional<Symbol> scope = findRankProfileScope(node);
            if (scope.isPresent()) {
                node.setSymbol(SymbolType.FUNCTION, context.fileURI(), scope.get());

                if (context.schemaIndex().findSymbol(node.getSymbol()) == null) {
                    node.setSymbolStatus(SymbolStatus.DEFINITION);
                    context.schemaIndex().insertSymbolDefinition(node.getSymbol());
                }
            } else {
                node.setSymbol(SymbolType.FUNCTION, context.fileURI());
                node.setSymbolStatus(SymbolStatus.INVALID);
            }
        }

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

    private Optional<Symbol> findRankProfileScope(SchemaNode innerNode) {
        while (innerNode != null) {
            if (innerNode.isASTInstance(rankProfile.class)) break;
            innerNode = innerNode.getParent();
        }

        if (innerNode == null || innerNode.size() < 2) return Optional.empty();

        SchemaNode rankProfileDefinitionNode = innerNode.get(1);

        if (!rankProfileDefinitionNode.hasSymbol() || rankProfileDefinitionNode.getSymbol().getStatus() != SymbolStatus.DEFINITION) return Optional.empty();

        return Optional.of(rankProfileDefinitionNode.getSymbol());
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
}
