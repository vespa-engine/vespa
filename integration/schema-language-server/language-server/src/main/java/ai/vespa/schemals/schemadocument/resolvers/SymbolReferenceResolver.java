package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public class SymbolReferenceResolver {

    public static List<Diagnostic> resolveSymbolReferences(ParseContext context, SchemaNode CST) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        resolveSymbolReferencesImpl(CST, context, diagnostics);
        return diagnostics;
    }

    private static void resolveSymbolReferencesImpl(SchemaNode node, ParseContext context, List<Diagnostic> diagnostics) {
        if (node.hasSymbol() && node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
            Optional<Symbol> referencedSymbol = Optional.empty();
            // dataType is handled separately
            SymbolType referencedType = node.getSymbol().getType();
            if (referencedType == SymbolType.SUBFIELD) {
                SchemaNode parentField = node.getPreviousSibling();
                Optional<Symbol> parentFieldDefinition = Optional.empty();

                // Two cases for where the parent field is defined. Either inside a struct or "global". 
                if (parentField.hasSymbol() && parentField.getSymbol().getType() == SymbolType.FIELD && parentField.getSymbol().getStatus() == SymbolStatus.REFERENCE) {
                    parentFieldDefinition = Optional.ofNullable(context.schemaIndex().findSymbol(context.fileURI(), SymbolType.FIELD, parentField.getText()));

                } else if (parentField.hasSymbol() && parentField.getSymbol().getType() == SymbolType.FIELD_IN_STRUCT && parentField.getSymbol().getStatus() == SymbolStatus.REFERENCE) {
                    parentFieldDefinition = context.schemaIndex().findDefinitionOfReference(parentField.getSymbol());
                } else if (parentField.hasSymbol() && parentField.getSymbol().getType() == SymbolType.MAP_VALUE && parentField.getSymbol().getStatus() == SymbolStatus.REFERENCE) {
                    parentFieldDefinition = context.schemaIndex().findDefinitionOfReference(parentField.getSymbol());
                } else if (parentField.hasSymbol() && parentField.getSymbol().getType() == SymbolType.STRUCT && parentField.getSymbol().getStatus() == SymbolStatus.REFERENCE) {
                    parentFieldDefinition = context.schemaIndex().findDefinitionOfReference(parentField.getSymbol());
                }

                if (parentFieldDefinition.isPresent()) {
                    referencedSymbol = resolveSubFieldReference(node, parentFieldDefinition.get(), context);
                }
            } else if (referencedType == SymbolType.RANK_PROFILE) {
                referencedSymbol = Optional.ofNullable(context.schemaIndex().findSymbolWithSchemaScope(node.getSymbol()));
            } else {
                referencedSymbol = Optional.ofNullable(context.schemaIndex().findSymbol(node.getSymbol()));
            }

            if (referencedSymbol.isPresent()) {
                node.setSymbolStatus(SymbolStatus.REFERENCE);
                context.schemaIndex().insertSymbolReference(referencedSymbol.get(), node.getSymbol());
            } else {
                diagnostics.add(new Diagnostic(
                    node.getRange(),
                    "Undefined symbol " + node.getText(),
                    DiagnosticSeverity.Error,
                    ""
                ));
            }
        }

        for (SchemaNode child : node) {
            resolveSymbolReferencesImpl(child, context, diagnostics);
        }

        //if (node.hasSymbol()) {
        //    diagnostics.add(new Diagnostic(node.getRange(), node.getSymbol().getLongIdentifier(), DiagnosticSeverity.Information, node.getSymbol().getType().toString() + " " + node.getSymbol().getStatus().toString()));
        //}

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
    private static Optional<Symbol> resolveSubFieldReference(SchemaNode node, Symbol fieldDefinition, ParseContext context) {
        if (fieldDefinition.getType() != SymbolType.FIELD 
            && fieldDefinition.getType() != SymbolType.FIELD_IN_STRUCT 
            && fieldDefinition.getType() != SymbolType.MAP_VALUE
            && fieldDefinition.getType() != SymbolType.STRUCT) return Optional.empty();
        if (fieldDefinition.getStatus() != SymbolStatus.DEFINITION) return Optional.empty();

        if (fieldDefinition.getType() == SymbolType.STRUCT) {
            return resolveFieldInStructReference(node, fieldDefinition, context);
        }

        SchemaNode dataTypeNode = null;
        Optional<Symbol> referencedSymbol = Optional.empty();
        if (fieldDefinition.getType() == SymbolType.MAP_VALUE) {
            dataTypeNode = fieldDefinition.getNode();
        } else if (fieldDefinition.getType() == SymbolType.FIELD || fieldDefinition.getType() == SymbolType.FIELD_IN_STRUCT) {
            dataTypeNode = fieldDefinition.getNode().getNextSibling().getNextSibling();
            if (!dataTypeNode.isASTInstance(dataType.class)) return Optional.empty();


            if (dataTypeNode.hasSymbol()) {
                // TODO: handle annotation reference and document reference?
                if (!isStructReference(dataTypeNode)) return Optional.empty();

                Symbol structReference = dataTypeNode.getSymbol();
                Symbol structDefinition = context.schemaIndex().findSymbol(context.fileURI(), SymbolType.STRUCT, structReference.getLongIdentifier());
                return resolveFieldInStructReference(node, structDefinition, context);
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
            Symbol structDefinition = context.schemaIndex().findSymbol(context.fileURI(), SymbolType.STRUCT, structReference.getLongIdentifier());

            if (structDefinition == null) return Optional.empty();

            return resolveFieldInStructReference(node, structDefinition, context);
        }
        return referencedSymbol;
    }

    private static Optional<Symbol> resolveFieldInStructReference(SchemaNode node, Symbol structDefinition, ParseContext context) {
        Optional<Symbol> referencedSymbol = Optional.empty();
        referencedSymbol = context.schemaIndex().findFieldInStruct(structDefinition, node.getText());
        //referencedSymbol = Optional.ofNullable(context.schemaIndex().findSymbol(context.fileURI(), SymbolType.FIELD_IN_STRUCT, structDefinition.getLongIdentifier() + "." + node.getText()));
        referencedSymbol.ifPresent(symbol -> node.setSymbolType(symbol.getType()));
        return referencedSymbol;
    }

    private static Optional<Symbol> resolveMapValueReference(SchemaNode node, Symbol mapValueDefinition, ParseContext context) {
        Optional<Symbol> referencedSymbol = Optional.empty();
        if (node.getText().equals("key")) {
            referencedSymbol = Optional.ofNullable(
                context.schemaIndex().findSymbol(context.fileURI(), SymbolType.MAP_KEY, mapValueDefinition.getLongIdentifier() + ".key")
            );

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

    private static Optional<Symbol> findMapValueDefinition(ParseContext context, Symbol mapValueDefinition) {
        Symbol rawValueSymbol = context.schemaIndex().findSymbol()
    }
}
