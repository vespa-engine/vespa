package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

import com.yahoo.schema.parser.ParsedType.Variant;
import com.yahoo.schema.processing.ReservedFunctionNames;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.TensorTypeParser;
import com.yahoo.tensor.TensorType.MappedDimension;

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.AS;
import ai.vespa.schemals.parser.ast.RootRankProfile;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.fieldOutsideDoc;
import ai.vespa.schemals.parser.ast.functionElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.importField;
import ai.vespa.schemals.parser.ast.mapDataType;
import ai.vespa.schemals.parser.ast.namedDocument;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.structFieldDefinition;
import ai.vespa.schemals.parser.ast.tensorTypeElm;
import ai.vespa.schemals.parser.rankingexpression.ast.lambdaFunction;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.Node.LanguageType;

/**
 * IdentifySymbolDefinition identifies symbol definitions, and mark the SchemaNode as a symbol and adds it to the index
 */
public class IdentifySymbolDefinition extends Identifier<SchemaNode> {

    public IdentifySymbolDefinition(ParseContext context) {
		super(context);
	}


    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        if (node.isASTInstance(dataType.class)) {
            handleDataTypeDefinition(node, ret);
            return ret;
        }

        if (node.getLanguageType() == LanguageType.RANK_EXPRESSION) {
            return identifyDefinitionInRankExpression(node);
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
                if (symbolType == SymbolType.RANK_PROFILE && parent.getParent() != null && parent.getParent().isASTInstance(RootRankProfile.class)) {
                    // we are in a rank-profile file (.profile)
                    String workspaceRootURI = context.scheduler().getWorkspaceURI();
                    if (workspaceRootURI == null) return ret;
                    String currentURI = context.fileURI();

                    String schemaName = FileUtils.firstPathComponentAfterPrefix(currentURI, workspaceRootURI);

                    if (schemaName == null) return ret;

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

            Optional<Symbol> existingSymbol = context.schemaIndex().findSymbolInScope(node.getSymbol());

            if (existingSymbol.isEmpty()) {
                node.setSymbolStatus(SymbolStatus.DEFINITION);
                context.schemaIndex().insertSymbolDefinition(node.getSymbol());

                if (node.getSymbol().getType() == SymbolType.FUNCTION) {
                    verifySymbolFunctionName(node, ret);
                }

            } else {
                node.setSymbolStatus(SymbolStatus.INVALID);


                if (symbolType == SymbolType.FIELD) {
                    Range range = null;

                    if (parent.getParent().isASTInstance(fieldOutsideDoc.class)) {
                        range = node.getRange();
                    } else if (!context.fieldIndex().getIsInsideDoc(existingSymbol.get())) {
                        range = existingSymbol.get().getNode().getRange();
                    }

                    if (range != null)
                        ret.add(new SchemaDiagnostic.Builder()
                            .setRange(range)
                            .setMessage("Field '" + node.getText() + "' shadows a document field with the same name.")
                            .setSeverity(DiagnosticSeverity.Warning)
                            .build());
                }
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
        if (node.getParent() == null)return;

        if (node.getParent().isASTInstance(mapDataType.class)) {

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

        if (node.isASTInstance(tensorTypeElm.class)) {
            Optional<Symbol> scope = CSTUtils.findScope(node);

            if (!scope.isPresent()) return;

            try {
                TensorType tensorType = TensorTypeParser.fromSpec(node.getText());

                node.setSymbol(SymbolType.TENSOR, context.fileURI(), scope.get());
                node.setSymbolStatus(SymbolStatus.DEFINITION);
                context.schemaIndex().insertSymbolDefinition(node.getSymbol());

                for (var dimension : tensorType.dimensions()) {
                    SymbolType type = (dimension instanceof MappedDimension) ? SymbolType.TENSOR_DIMENSION_MAPPED : SymbolType.TENSOR_DIMENSION_INDEXED;
                    Symbol dimensionSymbol = new Symbol(node, type, context.fileURI(), node.getSymbol(), dimension.name());
                    dimensionSymbol.setStatus(SymbolStatus.DEFINITION);
                    context.schemaIndex().insertSymbolDefinition(dimensionSymbol);
                }
            } catch(Exception e) {
                // ignore
            }
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

    private ArrayList<Diagnostic> identifyDefinitionInRankExpression(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isASTInstance(ai.vespa.schemals.parser.rankingexpression.ast.identifierStr.class)) {
            return ret;
        }

        SchemaNode grandParent = node.getParent(2);
        if (grandParent == null || !grandParent.isASTInstance(lambdaFunction.class) || grandParent.size() < 1) {
            return ret;
        }

        SchemaNode parent = grandParent.get(0);

        if (!parent.hasSymbol()) {

            Optional<Symbol> parentScope = CSTUtils.findScope(parent);
    
            if (parentScope.isEmpty()) {
                return ret;
            }
    
            parent.setSymbol(SymbolType.LAMBDA_FUNCTION, context.fileURI(), parentScope.get(), "lambda_" + node.hashCode());
            parent.setSymbolStatus(SymbolStatus.DEFINITION);
            context.schemaIndex().insertSymbolDefinition(parent.getSymbol());
        }

        
        node.setSymbol(SymbolType.PARAMETER, context.fileURI(), parent.getSymbol());

        if (context.schemaIndex().findSymbolsInScope(node.getSymbol()).size() == 0) {
            node.setSymbolStatus(SymbolStatus.DEFINITION);
            context.schemaIndex().insertSymbolDefinition(node.getSymbol());
        } else {
            node.setSymbolStatus(SymbolStatus.INVALID);
        }

        return ret;
    }

    private static final Set<String> reservedFunctionNames = ReservedFunctionNames.getReservedNames();
    // TODO: Maybe add distance and bm25 to the list?
    private void verifySymbolFunctionName(SchemaNode node, List<Diagnostic> diagnostics) {
        String functionName = node.getSymbol().getShortIdentifier();
        if (reservedFunctionNames.contains(functionName)) {
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(node.getRange())
                .setMessage("Function '" + node.getText() + "' has a reserved name. This might mean that the function shadows the built-in function with the same name.")
                .setSeverity(DiagnosticSeverity.Warning)
                .build());
        }
    }
}
