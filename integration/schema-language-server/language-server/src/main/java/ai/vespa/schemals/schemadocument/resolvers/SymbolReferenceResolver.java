package ai.vespa.schemals.schemadocument.resolvers;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.schema.parser.ParsedType.Variant;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.FunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.REFERENCE;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.importField;
import ai.vespa.schemals.parser.ast.mapDataType;
import ai.vespa.schemals.parser.indexinglanguage.ast.DOT;
import ai.vespa.schemals.parser.rankingexpression.ast.BaseNode;
import ai.vespa.schemals.parser.rankingexpression.ast.args;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.BuiltInFunctions;
import ai.vespa.schemals.tree.SchemaNode;

public class SymbolReferenceResolver {
    public static void resolveSymbolReference(SchemaNode node, ParseContext context, List<Diagnostic> diagnostics) {
        Optional<Symbol> referencedSymbol = Optional.empty();
        // dataType is handled separately
        SymbolType referencedType = node.getSymbol().getType();
        if (referencedType == SymbolType.SUBFIELD) {
            SchemaNode parentField = node.getPreviousSibling();
            if (parentField.isASTInstance(DOT.class))parentField = parentField.getPreviousSibling();
            Optional<Symbol> parentFieldDefinition = Optional.empty();

            // Two cases for where the parent field is defined. Either inside a struct or "global". 

            if (parentField.hasSymbol() && parentField.getSymbol().getStatus() == SymbolStatus.REFERENCE) {
                parentFieldDefinition = context.schemaIndex().getSymbolDefinition(parentField.getSymbol());
            }
            if (parentFieldDefinition.isPresent()) {
                referencedSymbol = resolveSubFieldReference(node, parentFieldDefinition.get(), context, diagnostics);
            }
        } else if (referencedType == SymbolType.FUNCTION) {
            referencedSymbol = context.schemaIndex().findSymbol(node.getSymbol().getScope(), SymbolType.FUNCTION, node.getSymbol().getShortIdentifier().toLowerCase());

            if (referencedSymbol.isEmpty()) {
                // could be built in
                SchemaNode parent = node.getParent();

                if ((parent.getOriginalRankExpressionNode() instanceof BaseNode) && ((BaseNode)parent.getOriginalRankExpressionNode()).expressionNode instanceof FunctionNode) {
                    node.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
                    return;
                }

                if (BuiltInFunctions.simpleBuiltInFunctionsSet.contains(node.getSymbol().getShortIdentifier())) {
                    node.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
                    return;
                }
                //if (BuiltInFunctions.rankExpressionBultInFunctions.get(identifier) != null) {
                //    node.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
                //}
            }
        } else if (referencedType == SymbolType.TYPE_UNKNOWN) {

            if (didExpectLabel(node, diagnostics)) {
                node.setSymbolType(SymbolType.LABEL);
                node.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
                return;
            }

            // These roughly go from narrow to broad scope.
            SymbolType[] possibleTypes = new SymbolType[] { SymbolType.PARAMETER, SymbolType.FUNCTION, SymbolType.RANK_CONSTANT, SymbolType.FIELD };

            for (SymbolType type : possibleTypes) {
                referencedSymbol = context.schemaIndex().findSymbol(node.getSymbol().getScope(), type, node.getSymbol().getShortIdentifier().toLowerCase());
                if (referencedSymbol.isPresent()) {
                    node.setSymbolType(type);
                    break;
                }
            }

            if (referencedSymbol.isEmpty() && BuiltInFunctions.simpleBuiltInFunctionsSet.contains(node.getSymbol().getShortIdentifier())) {
                node.setSymbolType(SymbolType.FUNCTION);
                node.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
                return;
            }
        } else {
            referencedSymbol = context.schemaIndex().findSymbol(node.getSymbol());
        }

        if (referencedSymbol.isPresent()) {
            node.setSymbolStatus(SymbolStatus.REFERENCE);
            context.schemaIndex().insertSymbolReference(referencedSymbol.get(), node.getSymbol());
        } else {
            diagnostics.add(new SchemaDiagnostic.Builder()
                    .setRange( node.getRange())
                    .setMessage( "Undefined symbol " + node.getText())
                    .setSeverity( DiagnosticSeverity.Error)
                    .build() );
        }
    }

    /**
     * This finds the definition of a field inside a struct, where the struct is used as a type for some parent field
     * It solves the case where we have 
     * struct foo { field bar }
     * field baz type foo {}
     * And try to access baz.bar
     *
     * In this case baz is the parent field and bar is the subfield
     *
     * @param node the node pointing to the subfield declaration 
     * @param fieldDefinition the symbol where the parent field is *defined*
     * @param context 
     *
     * @return the definition of the field inside the struct if found 
     */
    public static Optional<Symbol> resolveSubFieldReference(SchemaNode node, Symbol fieldDefinition, ParseContext context, List<Diagnostic> diagnostics) {
        if (fieldDefinition.getType() != SymbolType.FIELD 
            && fieldDefinition.getType() != SymbolType.MAP_VALUE
            && fieldDefinition.getType() != SymbolType.STRUCT) return Optional.empty();
        if (fieldDefinition.getStatus() != SymbolStatus.DEFINITION) return Optional.empty();

        if (fieldDefinition.getType() == SymbolType.STRUCT) {
            return resolveFieldInStructReference(node, fieldDefinition, context);
        }

        // First check for struct-field definitions
        if (fieldDefinition.getType() == SymbolType.FIELD) {
            Optional<Symbol> structFieldDefinition = context.schemaIndex().findSymbolInScope(fieldDefinition, SymbolType.FIELD, node.getText().toLowerCase());
            if (structFieldDefinition.isPresent()) {
                node.setSymbolType(SymbolType.FIELD);
                return structFieldDefinition;
            }
        }

        SchemaNode dataTypeNode = null;
        Optional<Symbol> referencedSymbol = Optional.empty();
        if (fieldDefinition.getType() == SymbolType.MAP_VALUE) {
            dataTypeNode = fieldDefinition.getNode();
        } else if (fieldDefinition.getType() == SymbolType.FIELD) {
            if (fieldDefinition.getNode().getNextSibling() == null || fieldDefinition.getNode().getNextSibling().getNextSibling() == null) return Optional.empty();
            dataTypeNode = fieldDefinition.getNode().getNextSibling().getNextSibling();
            if (!dataTypeNode.isASTInstance(dataType.class)) return Optional.empty();


            if (dataTypeNode.hasSymbol()) {
                // TODO: handle annotation reference and document reference?
                if (!isStructReference(dataTypeNode)) return Optional.empty();

                Symbol structReference = dataTypeNode.getSymbol();
                Symbol structDefinition = context.schemaIndex().getSymbolDefinition(structReference).get();
                return resolveFieldInStructReference(node, structDefinition, context);
            } else if (dataTypeNode.get(0).isASTInstance(REFERENCE.class)) {
                if (dataTypeNode.size() < 3 || !dataTypeNode.get(2).get(0).hasSymbol()) return Optional.empty();
                Symbol documentReference = dataTypeNode.get(2).get(0).getSymbol();

                Optional<Symbol> documentDefinition = Optional.empty();

                if (documentReference.getStatus() == SymbolStatus.REFERENCE) {
                    documentDefinition = context.schemaIndex().getSymbolDefinition(documentReference);
                } else {
                    documentDefinition = context.schemaIndex().findSymbol(documentReference);
                }

                if (documentDefinition.isEmpty()) return Optional.empty();

                referencedSymbol = context.schemaIndex().findSymbol(documentDefinition.get(), SymbolType.FIELD, node.getText().toLowerCase());

                if (referencedSymbol.isPresent()) {
                    node.setSymbolType(referencedSymbol.get().getType());
                    var referencedSymbolIndexingTypes = context.fieldIndex().getFieldIndexingTypes(referencedSymbol.get());

                    // We identified the reference and found the definition,
                    // however this case is actually only valid in an import field statement.
                    // So we should add an error if thats not the case
                    if (!node.getParent().isASTInstance(importField.class)) {
                        diagnostics.add(new SchemaDiagnostic.Builder()
                                .setRange( node.getRange())
                                .setMessage( "Field " + referencedSymbol.get().getLongIdentifier() + " can not be accessed directly. Hint: Add an import field statement to access the field.")
                                .setSeverity( DiagnosticSeverity.Error)
                                .setCode(DiagnosticCode.ACCESS_UNIMPORTED_FIELD)
                                .build());
                        return referencedSymbol;
                    }
                    if (!referencedSymbolIndexingTypes.contains(IndexingType.ATTRIBUTE)) {
                        // TODO: quickfix
                        diagnostics.add(new SchemaDiagnostic.Builder()
                                .setRange( node.getRange())
                                .setMessage( "Cannot import " + referencedSymbol.get().getLongIdentifier() + " because it is not an attribute field. Only attribute fields can be imported.")
                                .setSeverity( DiagnosticSeverity.Error)
                                .build() );
                    } else if (referencedSymbolIndexingTypes.contains(IndexingType.INDEX)) {
                        // TODO: quickfix
                        diagnostics.add(new SchemaDiagnostic.Builder()
                                .setRange( node.getRange())
                                .setMessage( "Cannot import " + referencedSymbol.get().getLongIdentifier() + " because it is an index field. Importing index fields is not supported.")
                                .setSeverity( DiagnosticSeverity.Error)
                                .build() );
                    }

                    Symbol importFieldDefinitionSymbol = node.getNextSibling().getNextSibling().getSymbol();
                    if (importFieldDefinitionSymbol != null && importFieldDefinitionSymbol.getStatus() == SymbolStatus.DEFINITION) {
                        for (IndexingType indexingType : referencedSymbolIndexingTypes) {
                            context.fieldIndex().addFieldIndexingType(importFieldDefinitionSymbol, indexingType);
                        }
                    }
                }

                return referencedSymbol;
            }
        } else {
            return Optional.empty();
        }


        dataType originalNode = (dataType)dataTypeNode.getOriginalSchemaNode();
        if (originalNode.getParsedType().getVariant() == Variant.MAP) {
            return resolveMapValueReference(node, fieldDefinition, context);
        } else if (originalNode.getParsedType().getVariant() == Variant.ARRAY) {
            if (dataTypeNode.size() < 3 || !dataTypeNode.get(2).isASTInstance(dataType.class)) return Optional.empty();

            SchemaNode innerType = dataTypeNode.get(2);
            if (!isStructReference(innerType)) return Optional.empty();

            Symbol structReference = innerType.getSymbol();
            Symbol structDefinition = context.schemaIndex().getSymbolDefinition(structReference).get();

            return resolveFieldInStructReference(node, structDefinition, context);
        }
        return referencedSymbol;
    }

    private static Optional<Symbol> resolveFieldInStructReference(SchemaNode node, Symbol structDefinition, ParseContext context) {
        Optional<Symbol> referencedSymbol = context.schemaIndex().findSymbol(structDefinition, SymbolType.FIELD, node.getText().toLowerCase());

        if (referencedSymbol.isPresent()) {
            // TODO: maybe we could have a findSymbol that doesn't allow going up in scope
            if (!context.schemaIndex().isInScope(referencedSymbol.get(), structDefinition)) {
                return Optional.empty();
            }

            //referencedSymbol = Optional.ofNullable(context.schemaIndex().findSymbol(context.fileURI(), SymbolType.FIELD_IN_STRUCT, structDefinition.getLongIdentifier() + "." + node.getText()));
            node.setSymbolType(referencedSymbol.get().getType());
        }
        return referencedSymbol;
    }

    private static Optional<Symbol> resolveMapValueReference(SchemaNode node, Symbol mapValueDefinition, ParseContext context) {
        Optional<Symbol> referencedSymbol = Optional.empty();
        if (node.getText().equals("key")) {
            referencedSymbol = context.schemaIndex().findSymbol(mapValueDefinition, SymbolType.MAP_KEY, "key");

            referencedSymbol.ifPresent(symbol -> node.setSymbolType(SymbolType.MAP_KEY));
        }
        if (node.getText().equals("value")) {
            // For value there are two cases: either the map value is a primitive value with its own definition
            // or it is a reference to a struct
            referencedSymbol = findMapValueDefinition(context, mapValueDefinition);

            referencedSymbol.ifPresent(symbol -> node.setSymbolType(symbol.getType()));
        }
        return referencedSymbol;
    }

    private static boolean isStructReference(SchemaNode node) {
        return node != null && node.hasSymbol() && node.getSymbol().getType() == SymbolType.STRUCT && node.getSymbol().getStatus() == SymbolStatus.REFERENCE;
    }

    private static Optional<Symbol> findMapValueDefinition(ParseContext context, Symbol fieldDefinition) {
        if (fieldDefinition.getType() != SymbolType.FIELD) return Optional.empty();
        SchemaNode dataTypeNode = fieldDefinition.getNode().getNextSibling().getNextSibling();

        if (dataTypeNode == null || !dataTypeNode.isASTInstance(dataType.class)) return Optional.empty();

        if (dataTypeNode.size() == 0 || !dataTypeNode.get(0).isASTInstance(mapDataType.class)) return Optional.empty();

        SchemaNode valueNode = dataTypeNode.get(0).get(4);

        if (!valueNode.hasSymbol()) return Optional.empty();

        switch(valueNode.getSymbol().getStatus()) {
            case DEFINITION:
                return Optional.of(valueNode.getSymbol());
			case REFERENCE:
                return context.schemaIndex().getSymbolDefinition(valueNode.getSymbol());
            case BUILTIN_REFERENCE:
            case INVALID:
			case UNRESOLVED:
            default:
                return Optional.empty();
        }
    }

    /*
     * Some built in functions expect "labels", which are more like string literals than symbols we can work with
     * This function returns true if that is the case (and we should unset the symbol at the given node)
     * TODO: refactor. Not everything here should be label
     */
    private static boolean didExpectLabel(SchemaNode node, List<Diagnostic> diagnostics) {
        SchemaNode argsNode = node.getParent();
        SchemaNode argsChild = node;

        while (argsNode != null && !argsNode.isASTInstance(args.class)) {
            argsChild = argsNode;
            argsNode = argsNode.getParent();
        }

        if (argsNode == null) return false;

        SchemaNode identifierNode = argsNode.getParent().get(0);
        SchemaNode containingFeature = argsNode.getParent();

        // we can be certain that this is a ReferenceNode
        ReferenceNode functionExpressionNode = (ReferenceNode)((BaseNode)containingFeature.getOriginalRankExpressionNode()).expressionNode;
        ExpressionNode myArg = ((BaseNode)argsChild.getOriginalRankExpressionNode()).expressionNode;
        int myArgIndex = functionExpressionNode.children().indexOf(myArg);

        if (!identifierNode.hasSymbol() || identifierNode.getSymbol().getStatus() != SymbolStatus.BUILTIN_REFERENCE) return false;

        String functionIdentifier = identifierNode.getSymbol().getShortIdentifier();

        if (functionIdentifier.equals("itemRawScore")) {
            return true;
        }

        if (functionIdentifier.equals("closest")) {
            return myArgIndex == 1;
        }

        if (functionIdentifier.equals("distance") || functionIdentifier.equals("closeness")) {
            switch(myArgIndex) {
                case 0:
                    // TODO: not really label
                    return node.getSymbol().getShortIdentifier().equals("label") || node.getSymbol().getShortIdentifier().equals("field");
                case 1:
                    ExpressionNode firstArg = functionExpressionNode.children().get(0);
                    return ((firstArg instanceof ReferenceNode) && ((ReferenceNode)firstArg).getName().equals("label"));
                default:
                    return false;
            }
        }

        if (functionIdentifier.equals("tensorFromWeightedSet") || functionIdentifier.equals("tensorFromLabels")) {
            return myArgIndex == 1;
        }

        if (functionIdentifier.equals("query")) {
            // TODO: query gotodefinition could be nice to refer to inputs {} where possible
            return myArgIndex == 0;
        }

        return false;
    }
}
