package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;


import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
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
        POSITION,
        PREDICATE
    };

    public static final EnumSet<FieldType> AnyFieldType = EnumSet.allOf(FieldType.class);
    public static final EnumSet<FieldType> NumericOrTensorFieldType = EnumSet.of(FieldType.TENSOR, FieldType.NUMERIC);

    public static final EnumSet<FieldType> SingleValueOrArrayType = EnumSet.of(
        FieldType.NUMERIC,
        FieldType.STRING,
        FieldType.NUMERIC_ARRAY,
        FieldType.STRING_ARRAY
    );

    public static final EnumSet<IndexingType> IndexAttributeType = EnumSet.of(
        IndexingType.ATTRIBUTE,
        IndexingType.INDEX
    );

    public static record UnresolvedFieldArgument(
        SchemaNode node,
        Set<FieldType> fieldTypes,
        Set<IndexingType> indexingTypes
    ) {
    };

    private Set<FieldType> fieldTypes;
    private Set<IndexingType> indexingTypes;

    public FieldArgument(EnumSet<FieldType> fieldTypes, EnumSet<IndexingType> indexingTypes, String displayStr) {
        super(SymbolType.FIELD, displayStr);
        this.fieldTypes = fieldTypes;
        this.indexingTypes = indexingTypes;
    }

    public FieldArgument(EnumSet<FieldType> fieldTypes, EnumSet<IndexingType> indexingTypes) {
        this(fieldTypes, indexingTypes, "name");
    }

    public FieldArgument(EnumSet<FieldType> fieldTypes) {
        this(fieldTypes, EnumSet.noneOf(IndexingType.class));
    }

    public FieldArgument(FieldType fieldType) {
        this(EnumSet.of(fieldType));
    }

    public FieldArgument(FieldType fieldType, IndexingType indexingType) {
        this(EnumSet.of(fieldType), indexingType);
    }

    public FieldArgument(FieldType fieldType, IndexingType indexingType, String displayStr) {
        this(EnumSet.of(fieldType), indexingType, displayStr);
    }


    public FieldArgument(EnumSet<FieldType> fieldTypes, IndexingType indexingType) {
        this(fieldTypes, EnumSet.of(indexingType));
    }

    public FieldArgument(EnumSet<FieldType> fieldTypes, IndexingType indexingType, String displayStr) {
        this(fieldTypes, EnumSet.of(indexingType), displayStr);
    }

    public FieldArgument(FieldType fieldType, EnumSet<IndexingType> indexingTypes) {
        this(EnumSet.of(fieldType), indexingTypes);
    }

    public FieldArgument(FieldType fieldType, EnumSet<IndexingType> indexingTypes, String displayStr) {
        this(EnumSet.of(fieldType), indexingTypes, displayStr);
    }

    public FieldArgument() {
        this(AnyFieldType);
    }

    public FieldArgument(String displayStr) {
        this(AnyFieldType, EnumSet.noneOf(IndexingType.class), displayStr);
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        Optional<Diagnostic> diagnostic = super.parseArgument(context, node);

        // node is an expression node
        // field is a feature
        if (node.getChildren().size() > 0) {
            RankNode featureNode = node.getChildren().get(0);
            Optional<SchemaNode> firstProperty = featureNode.getProperty();
            if (firstProperty.isPresent()) {
                Node parentNode = firstProperty.get().getParent();
                Optional<Symbol> scope = CSTUtils.findScope(parentNode);

                for (int i = 0; i < parentNode.size(); i += 2) {
                    parentNode.get(i).get(0).setSymbol(SymbolType.SUBFIELD, context.fileURI(), scope);
                }
            }
        }

        if (diagnostic.isEmpty()) {
            SchemaNode unresolvedFieldArgument = super.findSymbolNode(node);
            context.addUnresolvedFieldArgument(new UnresolvedFieldArgument(unresolvedFieldArgument, fieldTypes, indexingTypes));
        }

        return diagnostic;
    }
}
