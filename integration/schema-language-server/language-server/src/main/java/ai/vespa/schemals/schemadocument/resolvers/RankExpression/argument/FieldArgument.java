package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;


import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public class FieldArgument extends SymbolArgument {

    public static enum FieldType {
        TENSOR,
        WSET,
        INTEGER,
        NUMERIC,
        NUMERIC_ARRAY,
        STRING,
        STRING_ARRAY,
        POSITION
    };

    public static final Set<FieldType> AnyFieldType = EnumSet.allOf(FieldType.class);
    public static final Set<FieldType> NumericOrTensorFieldType = new HashSet<>() {{
        add(FieldType.TENSOR);
        add(FieldType.NUMERIC);
    }};

    public static final Set<FieldType> SingleValueOrArrayType = new HashSet<>() {{
        add(FieldType.NUMERIC);
        add(FieldType.STRING);
        add(FieldType.NUMERIC_ARRAY);
        add(FieldType.STRING_ARRAY);
    }};

    public static final Set<IndexingType> IndexAttributeType = new HashSet<>() {{
        add(IndexingType.ATTRIBUTE);
        add(IndexingType.INDEX);
    }};

    public static record UnresolvedFieldArgument(
        SchemaNode node,
        Set<FieldType> fieldTypes,
        Set<IndexingType> indexingTypes
    ) {
    };

    private Set<FieldType> fieldTypes;
    private Set<IndexingType> indexingTypes;

    public FieldArgument(Set<FieldType> fieldTypes, Set<IndexingType> indexingTypes, String displayStr) {
        super(SymbolType.FIELD, displayStr);
        this.fieldTypes = fieldTypes;
        this.indexingTypes = indexingTypes;
    }

    public FieldArgument(Set<FieldType> fieldTypes, Set<IndexingType> indexingTypes) {
        this(fieldTypes, indexingTypes, "name");
    }

    public FieldArgument(Set<FieldType> fieldTypes) {
        this(fieldTypes, new HashSet<>());
    }

    public FieldArgument(FieldType fieldType) {
        this(new HashSet<>() {{
            add(fieldType);
        }});
    }

    public FieldArgument(FieldType fieldType, IndexingType indexingType) {
        this(new HashSet<>() {{
            add(fieldType);
        }}, indexingType);
    }

    public FieldArgument(FieldType fieldType, IndexingType indexingType, String displayStr) {
        this(new HashSet<>() {{
            add(fieldType);
        }}, indexingType, displayStr);
    }


    public FieldArgument(Set<FieldType> fieldTypes, IndexingType indexingType) {
        this(fieldTypes, new HashSet<>() {{
            add(indexingType);
        }});
    }

    public FieldArgument(Set<FieldType> fieldTypes, IndexingType indexingType, String displayStr) {
        this(fieldTypes, new HashSet<>() {{
            add(indexingType);
        }}, displayStr);
    }

    public FieldArgument(FieldType fieldType, Set<IndexingType> indexingTypes) {
        this(new HashSet<>() {{
            add(fieldType);
        }}, indexingTypes);
    }

    public FieldArgument() {
        this(AnyFieldType);
    }

    public FieldArgument(String displayStr) {
        this(AnyFieldType, new HashSet<>(), displayStr);
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        Optional<Diagnostic> diagnositc = super.parseArgument(context, node);

        if (diagnositc.isPresent()) {
            SchemaNode unresolvedFieldArgument = super.findSymbolNode(node);
            context.addUnresolvedFieldArgument(new UnresolvedFieldArgument(unresolvedFieldArgument, fieldTypes, indexingTypes));
        }

        return diagnositc;
    }
}
