package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.AS;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.functionElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.importField;
import ai.vespa.schemals.parser.ast.mapDataType;
import ai.vespa.schemals.parser.ast.namedDocument;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.structFieldDefinition;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifySymbolDefinition extends Identifier {

    public IdentifySymbolDefinition(ParseContext context) {
		super(context);
	}


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

        if (parent.isASTInstance(importField.class) && node.getPreviousSibling() != null && node.getPreviousSibling().isASTInstance(AS.class)) {
            createSymbol(node, SymbolType.FIELD);
            return ret;
        }

        // Prevent inheritance from beeing marked as a definition
        if (parent.indexOf(node) >= 3) {
            // Unnless it is a paramenter to a function
            if (parent.isASTInstance(functionElm.class) && node.isASTInstance(identifierStr.class)) {
                createSymbol(node, SymbolType.PARAMETER);
            }

            return ret;
        }

        Map<Class<?>, SymbolType> searchMap = isIdentifier ? SchemaIndex.IDENTIFIER_TYPE_MAP : SchemaIndex.IDENTIFIER_WITH_DASH_TYPE_MAP;
        SymbolType symbolType = searchMap.get(parent.getASTClass());
        if (symbolType != null) {

            if (parent.isASTInstance(namedDocument.class) || parent.isASTInstance(rootSchema.class)) {
                node.setSymbol(symbolType, context.fileURI());
                node.setSymbolStatus(SymbolStatus.DEFINITION);
                context.schemaIndex().insertSymbolDefinition(node.getSymbol());
                return ret;
            }

            Optional<Symbol> scope = CSTUtils.findScope(node);
            if (scope.isEmpty()) {
                if (symbolType == SymbolType.RANK_PROFILE && parent.getParent() == null) {
                    // we are in a rank-profile file (.profile)
                    String workspaceRootURI = context.schemaIndex().getWorkspaceURI();
                    String currentURI = context.fileURI();

                    if (!currentURI.startsWith(workspaceRootURI)) return ret; // some invalid situation

                    String suffix = currentURI.substring(workspaceRootURI.length());
                    if (suffix.startsWith("/"))suffix = suffix.substring(1);

                    String[] components = suffix.split("/");

                    if (components.length == 0) return ret;
                    String schemaName = components[0];

                    Optional<Symbol> schemaSymbol = context.schemaIndex().getSchemaDefinition(schemaName);

                    if (schemaSymbol.isEmpty()) return ret;

                    // TODO: rank-profile belonging to namedDocument??
                    node.setSymbol(symbolType, context.fileURI(), schemaSymbol.get());
                    node.setSymbolStatus(SymbolStatus.DEFINITION);
                    context.schemaIndex().insertSymbolDefinition(node.getSymbol());
                }
                return ret;
            }

            node.setSymbol(symbolType, context.fileURI(), scope.get());

            if (context.schemaIndex().findSymbol(node.getSymbol()).isEmpty()) {
                node.setSymbolStatus(SymbolStatus.DEFINITION);
                context.schemaIndex().insertSymbolDefinition(node.getSymbol());
            } else {
                node.setSymbolStatus(SymbolStatus.INVALID);
            }

            return ret;
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

        Optional<Symbol> scope = CSTUtils.findScope(node);

        if (scope.isPresent()) {
            node.setSymbol(type, context.fileURI(), scope.get());
        } else {
            node.setSymbol(type, context.fileURI());
        }

        node.setSymbolStatus(SymbolStatus.DEFINITION);
        context.schemaIndex().insertSymbolDefinition(node.getSymbol());
    }
}
