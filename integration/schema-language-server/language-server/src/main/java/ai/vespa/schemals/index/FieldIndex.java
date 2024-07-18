package ai.vespa.schemals.index;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.importField;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * FieldIndex
 * For storing metadata about fields.
 * It is owned by a SchemaIndex, so there is a one-to-one correspondence
 */
public class FieldIndex {

    /*
     * Simulating a struct because record is immutable
     */
    public class FieldIndexEntry {
        public SchemaNode dataTypeNode;

        FieldIndexEntry(SchemaNode dataTypeNode) {
            this.dataTypeNode = dataTypeNode;
        }
    }

    // Key is a field definition symbol.
    private Map<Symbol, FieldIndexEntry> database = new HashMap<>();
    private PrintStream logger;
    private SchemaIndex schemaIndex;

    public FieldIndex(PrintStream logger, SchemaIndex schemaIndex) {
        this.logger = logger;
        this.schemaIndex = schemaIndex;
    }

    public void clearFieldsByURI(String fileURI) {
        for (Iterator<Map.Entry<Symbol, FieldIndexEntry>> it = database.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Symbol, FieldIndexEntry> entry = it.next();
            if (entry.getKey().getFileURI().equals(fileURI)) {
                it.remove();
            }
        }
    }

    public void insertFieldDefinition(Symbol fieldDefinition) {
        if (fieldDefinition.getStatus() != SymbolStatus.DEFINITION || fieldDefinition.getType() != SymbolType.FIELD) {
            throw new IllegalArgumentException("Only field definitions should be stored in FieldIndex!");
        }

        SchemaNode dataTypeNode = resolveFieldDataTypeNode(fieldDefinition);
        database.put(fieldDefinition, new FieldIndexEntry(dataTypeNode));
    }

    public Optional<SchemaNode> getFieldDataTypeNode(Symbol fieldDefinition) {
        FieldIndexEntry entry = database.get(fieldDefinition);
        if (entry == null) return Optional.empty();
        if (entry.dataTypeNode != null) return Optional.of(entry.dataTypeNode);

        // Try to resolve it
        entry.dataTypeNode = resolveFieldDataTypeNode(fieldDefinition);
        return Optional.ofNullable(entry.dataTypeNode);
    }

    /**
     * Try to find the node that holds the dataType element
     * Also try to not fall into an infinite loop
     */
    private SchemaNode resolveFieldDataTypeNode(Symbol fieldDefinition) { return resolveFieldDataTypeNode(fieldDefinition, new HashSet<>()); }

    private SchemaNode resolveFieldDataTypeNode(Symbol fieldDefinition, Set<Symbol> visited) {
        if (visited.contains(fieldDefinition)) return null;
        visited.add(fieldDefinition);
        SchemaNode fieldDefinitionNode = fieldDefinition.getNode();

        if (fieldDefinitionNode.getParent().isASTInstance(importField.class)) {
            SchemaNode importReferenceNode = fieldDefinitionNode.getPreviousSibling().getPreviousSibling();
            if (!importReferenceNode.hasSymbol() || importReferenceNode.getSymbol().getStatus() != SymbolStatus.REFERENCE) return null;
            Optional<Symbol> referencedField = schemaIndex.getSymbolDefinition(importReferenceNode.getSymbol());
            if (referencedField.isEmpty()) return null;
            return resolveFieldDataTypeNode(referencedField.get(), visited);
        } else if (fieldDefinitionNode.getNextSibling() != null && fieldDefinitionNode.getNextSibling().getNextSibling() != null && fieldDefinitionNode.getNextSibling().getNextSibling().isASTInstance(dataType.class)) {
            return fieldDefinitionNode.getNextSibling().getNextSibling();
        }
        return null;
    }
}
