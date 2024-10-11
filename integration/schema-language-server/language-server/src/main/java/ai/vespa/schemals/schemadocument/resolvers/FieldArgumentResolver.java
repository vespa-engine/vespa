package ai.vespa.schemals.schemadocument.resolvers;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.FieldArgument.UnresolvedFieldArgument;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * This checks that the type or indexing settings for a referenced field are correct.
 * For instance, some uses require that a field has indexing 'attribute'. Other uses require that a field is of a certain type 
 * or set of types.
 * The information about the required data is in class {@link ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.FieldArgument.UnresolvedFieldArgument}
 * The resolver then looks up the field definition and chcks if the registered indexing settings are correct.
 */
public class FieldArgumentResolver {
    public static Optional<Diagnostic> resolveFieldArgument(ParseContext context, UnresolvedFieldArgument fieldArgument) {

        if (fieldArgument.indexingTypes().size() == 0) {
            return Optional.empty();
        }

        SchemaNode node = fieldArgument.node();
        if (!node.hasSymbol()) {
            return Optional.empty();
        }
        Symbol symbol = node.getSymbol();

        Optional<Symbol> symbolDefinition = context.schemaIndex().findSymbol(symbol);

        if (symbolDefinition.isEmpty()) {
            return Optional.empty();
        }

        EnumSet<IndexingType> indexingTypes = context.fieldIndex().getFieldIndexingTypes(symbolDefinition.get());

        boolean invalid = Collections.disjoint(indexingTypes, fieldArgument.indexingTypes());

        if (!invalid) {
            return Optional.empty();
        }

        EnumSet<IndexingType> missingFields = EnumSet.copyOf(fieldArgument.indexingTypes());
        missingFields.removeAll(indexingTypes);

        return Optional.of(new SchemaDiagnostic.Builder()
            .setRange(node.getRange())
            .setMessage("The referenced field are missing one of the following indexing types: " + missingFields)
            .setSeverity(DiagnosticSeverity.Error)
            .setCode(SchemaDiagnostic.DiagnosticCode.FIELD_ARGUMENT_MISSING_INDEXING_TYPE)
            .build());

    }
}
