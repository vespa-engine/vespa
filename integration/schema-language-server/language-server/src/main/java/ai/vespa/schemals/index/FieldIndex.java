package ai.vespa.schemals.index;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.fieldOutsideDoc;
import ai.vespa.schemals.parser.ast.importField;
import ai.vespa.schemals.tree.Node;

/**
 * FieldIndex
 * For storing metadata about fields.
 * It is owned by a SchemaIndex, so there is a one-to-one correspondence
 */
public class FieldIndex {

    public enum IndexingType {
        ATTRIBUTE,
        INDEX,
        SUMMARY
    }

    /*
     * Simulating a struct because record is immutable
     */
    public class FieldIndexEntry {
        public Node dataTypeNode;
        public EnumSet<IndexingType> indexingTypes = EnumSet.noneOf(IndexingType.class);
        public boolean isInsideDoc = true;

        FieldIndexEntry(Node dataTypeNode, boolean isInsideDoc) {
            this.dataTypeNode = dataTypeNode;
            this.isInsideDoc = isInsideDoc;
        }

        @Override
        public String toString() {
            return (dataTypeNode == null ? " unknown type" : dataTypeNode.toString()) + " " + indexingTypes.toString() + " " + (isInsideDoc ? " in document" : " in schema");
        }
    }

    // Key is a field definition symbol.
    private Map<Symbol, FieldIndexEntry> database = new HashMap<>();
    private ClientLogger logger;
    private SchemaIndex schemaIndex;

    public FieldIndex(ClientLogger logger, SchemaIndex schemaIndex) {
        this.logger = logger;
        this.schemaIndex = schemaIndex;
    }

    public void clearFieldsByURI(URI fileURI) {
        for (Iterator<Map.Entry<Symbol, FieldIndexEntry>> it = database.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Symbol, FieldIndexEntry> entry = it.next();
            if (entry.getKey().fileURIEquals(fileURI)) {
                it.remove();
            }
        }
    }

    public void insertFieldDefinition(Symbol fieldDefinition) {
        if (fieldDefinition.getStatus() != SymbolStatus.DEFINITION || fieldDefinition.getType() != SymbolType.FIELD) {
            throw new IllegalArgumentException("Only field definitions should be stored in FieldIndex!");
        }
        if (database.containsKey(fieldDefinition)) return;

        Node dataTypeNode = resolveFieldDataTypeNode(fieldDefinition);
        database.put(fieldDefinition, new FieldIndexEntry(dataTypeNode, resolveIsInsideDoc(fieldDefinition)));
    }

    public void addFieldIndexingType(Symbol fieldDefinition, IndexingType indexingType) {
        insertFieldDefinition(fieldDefinition);

        FieldIndexEntry entry = database.get(fieldDefinition);
        entry.indexingTypes.add(indexingType);

        // Attribute propagates from struct-field
        if (indexingType == IndexingType.ATTRIBUTE 
                && fieldDefinition.getScope() != null 
                && fieldDefinition.getScope().getType() == SymbolType.FIELD
                && fieldDefinition.getScope().getStatus() == SymbolStatus.DEFINITION) {
            addFieldIndexingType(fieldDefinition.getScope(), indexingType);
        }
    }

    public Optional<Node> getFieldDataTypeNode(Symbol fieldDefinition) {
        FieldIndexEntry entry = database.get(fieldDefinition);
        if (entry == null) return Optional.empty();
        if (entry.dataTypeNode != null) return Optional.of(entry.dataTypeNode);

        // Try to resolve it
        entry.dataTypeNode = resolveFieldDataTypeNode(fieldDefinition);
        return Optional.ofNullable(entry.dataTypeNode);
    }

    public EnumSet<IndexingType> getFieldIndexingTypes(Symbol fieldDefinition) {
        FieldIndexEntry entry = database.get(fieldDefinition);

        if (entry == null) return EnumSet.noneOf(IndexingType.class);

        return EnumSet.copyOf(entry.indexingTypes);
    }

    public boolean getIsInsideDoc(Symbol fieldDefinition) {
        FieldIndexEntry entry = database.get(fieldDefinition);
        if (entry == null) return false;
        return entry.isInsideDoc;
    }

    /*
     * Some fields have struct, array<struct>, map<int, struct> etc. as their data type.
     * Fields inside the struct (or key/value for the case of map) are accessible from the field
     * with '.'-syntax.
     * This function tries to find the definition of said struct (or map) if it exists.
     */
    public Optional<Symbol> findFieldStructDefinition(Symbol fieldDefinition) {
        Optional<Node> dataTypeNode = getFieldDataTypeNode(fieldDefinition);
        if (dataTypeNode.isEmpty()) return Optional.empty();

        if (dataTypeNode.get().hasSymbol()) {
            // TODO: handle non struct?
            if (dataTypeNode.get().getSymbol().getType() != SymbolType.STRUCT) return Optional.empty();

            return schemaIndex.getSymbolDefinition(dataTypeNode.get().getSymbol());
        }

        if (!dataTypeNode.get().isSchemaNode()) return Optional.empty();
        dataType originalNode = (dataType)dataTypeNode.get().getSchemaNode().getOriginalSchemaNode();
        
        if (originalNode.getParsedType().getVariant() == Variant.MAP) {
            return Optional.of(fieldDefinition);
        } else if (originalNode.getParsedType().getVariant() == Variant.ARRAY) {
            if (dataTypeNode.get().size() < 3 || !dataTypeNode.get().get(2).isASTInstance(dataType.class)) return Optional.empty();

            Node innerType = dataTypeNode.get().get(2);

            if (!innerType.hasSymbol() || innerType.getSymbol().getType() != SymbolType.STRUCT) return Optional.empty();

            Symbol structReference = innerType.getSymbol();
            return schemaIndex.getSymbolDefinition(structReference);
        }

        return Optional.empty();
    }

    /**
     * Try to find the node that holds the dataType element
     * Also try to not fall into an infinite loop. It could possibly happen if there are cyclic field references somehow
     */
    private Node resolveFieldDataTypeNode(Symbol fieldDefinition) { 
        fieldDefinition = schemaIndex.getFirstSymbolDefinition(fieldDefinition).get();
        return resolveFieldDataTypeNode(fieldDefinition, new HashSet<>()); 
    }

    private Node resolveFieldDataTypeNode(Symbol fieldDefinition, Set<Symbol> visited) {
        if (visited.contains(fieldDefinition)) return null;
        visited.add(fieldDefinition);
        Node fieldDefinitionNode = fieldDefinition.getNode();

        if (fieldDefinitionNode.isASTInstance(dataType.class)) {
            // For map key and value
            return fieldDefinitionNode;
        }

        if (fieldDefinitionNode.getParent().isASTInstance(importField.class)) {
            Node importReferenceNode = fieldDefinitionNode.getPreviousSibling().getPreviousSibling();
            if (!importReferenceNode.hasSymbol() || importReferenceNode.getSymbol().getStatus() != SymbolStatus.REFERENCE) return null;
            Optional<Symbol> referencedField = schemaIndex.getSymbolDefinition(importReferenceNode.getSymbol());
            if (referencedField.isEmpty()) return null;
            return resolveFieldDataTypeNode(referencedField.get(), visited);
        }

        if (fieldDefinitionNode.getNextSibling() != null && fieldDefinitionNode.getNextSibling().getNextSibling() != null && fieldDefinitionNode.getNextSibling().getNextSibling().isASTInstance(dataType.class)) {
            return fieldDefinitionNode.getNextSibling().getNextSibling();
        }         
        return null;
    }

    private boolean resolveIsInsideDoc(Symbol fieldDefinition) {
        // todo: struct field definition
        if (fieldDefinition.getScope() != null && fieldDefinition.getScope().getType() == SymbolType.FIELD && fieldDefinition.getScope().getStatus() == SymbolStatus.DEFINITION) {
            return resolveIsInsideDoc(fieldDefinition.getScope());
        }
        Node fieldDefinitionNode = fieldDefinition.getNode();
        if (fieldDefinitionNode.getParent().isASTInstance(importField.class)) return false;
        if (fieldDefinitionNode.getParent().getParent().isASTInstance(fieldOutsideDoc.class)) return false;
        return true;
    }

    public void dumpIndex() {
        for (var entry : database.entrySet()) {
            logger.info(entry.getKey().toString() + " -> " + entry.getValue().toString());
        }
    }
}
