// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.skill;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.FieldSet;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.searchlib.document.FastMapSearch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An agent skill describing the schemas of this application, telling agents what they can query and how,
 * including the explanations written in the schemas. Creates an overview (the SKILL.md document) listing the schemas,
 * and a document per schema with its fields, field sets and rank profiles, as markdown.
 *
 * @author ingrid
 */
public class SchemaSkill extends AbstractComponent {

    static final String NAME = "vespa-application-schemas";
    static final String DESCRIPTION = "The schemas, fields and rank profiles of this Vespa application, " +
                                      "with notes from its authors. Use this when writing queries against this application.";
    private static final String Z_CURVE_SUFFIX = "_zcurve";

    private final SchemaInfo schemaInfo;
    private final Map<String, String> schemaDocuments = new LinkedHashMap<>();

    @Inject
    public SchemaSkill(SchemaInfo schemaInfo) {
        this.schemaInfo = schemaInfo;
        schemaInfo.schemas().forEach((name, schema) -> schemaDocuments.put(name, renderSchema(schema, schemaInfo)));
    }

    /** Returns the overview of the schemas, which is the SKILL.md of the skill. */
    public String overview() {
        var md = new StringBuilder();
        md.append("---\n")
          .append("name: ").append(NAME).append("\n")
          .append("description: ").append(DESCRIPTION).append("\n")
          .append("---\n\n");
        md.append("# Querying this application\n\n")
          .append("Query this application using [YQL](https://docs.vespa.ai/en/reference/querying/yql.html)")
          .append(" at `/search/`.\n");
        if ( ! schemaInfo.schemas().isEmpty())
            md.append("\nEach schema below links to its fields, field sets and rank profiles. ")
              .append("Read it before querying the schema.\n");
        for (Schema schema : schemaInfo.schemas().values()) {
            md.append("\n## Schema `").append(schema.name()).append("`\n");
            appendSchemaInfo(schema, schemaInfo, md);
            md.append("\nDetails: `/skills/v1/schemas/").append(schema.name()).append("/`\n");
        }
        return md.toString();
    }

    /** Returns the document describing the given schema, or empty if there is no schema with this name. */
    public Optional<String> schema(String name) { return Optional.ofNullable(schemaDocuments.get(name)); }

    private static String renderSchema(Schema schema, SchemaInfo schemaInfo) {
        var md = new StringBuilder();
        md.append("# Schema `").append(schema.name()).append("`\n");
        appendSchemaInfo(schema, schemaInfo, md);

        List<Field> fields = schema.fields().values().stream()
                                   .filter(field -> ! isInternal(field, schema))
                                   .filter(field -> ! isStructField(field, schema))
                                   .toList();
        if ( ! fields.isEmpty()) {
            md.append("\n## Fields\n\n");
            for (Field field : fields) {
                appendField(field, schema, md, 0);
                // The searchable struct fields at any depth, by full name, as their parents may not be listed
                if (hasStructFields(field.type()))
                    structFields(field, schema).filter(SchemaSkill::isSearchable)
                                               .forEach(structField -> appendField(structField, schema, md, 1));
                field.fastMapSearches().forEach(fastMap -> appendLookup(field, fastMap, md));
            }
        }

        List<FieldSet> fieldSets = schema.fieldSets().values().stream().filter(fieldSet -> ! isBuiltIn(fieldSet)).toList();
        if ( ! fieldSets.isEmpty()) {
            md.append("\n## Field sets\n\n");
            for (FieldSet fieldSet : fieldSets)
                md.append("- `").append(fieldSet.name()).append("`: ")
                  .append(fieldSet.fieldNames().stream().map(SchemaSkill::code).collect(Collectors.joining(", ")))
                  .append("\n");
        }

        if ( ! schema.rankProfiles().isEmpty()) {
            md.append("\n## Rank profiles\n\n");
            for (RankProfile profile : schema.rankProfiles().values()) {
                md.append("- `").append(profile.name()).append("`");
                appendExplanation(profile.explanation(), md, 1);
                md.append("\n");
                for (RankProfile.Input input : profile.inputDetails().values()) {
                    md.append("  - Input `").append(input.name()).append("` (`").append(typeName(input.type())).append("`)");
                    appendExplanation(input.explanation(), md, 2);
                    md.append("\n");
                }
            }
        }
        return md.toString();
    }

    /** Appends the schema level information shared by the overview and the schema document. */
    private static void appendSchemaInfo(Schema schema, SchemaInfo schemaInfo, StringBuilder md) {
        schema.explanation().map(String::strip)
              .filter(text -> ! text.isEmpty())
              .ifPresent(text -> md.append("\n").append(text).append("\n"));
        if (isStreaming(schema, schemaInfo))
            md.append("\nThis schema uses streaming search: queries must set exactly one of `streaming.groupname` " +
                      "(for document ids with `g=<group>`), `streaming.userid` (for ids with `n=<number>`) " +
                      "or `streaming.selection` (a document selection).\n");
    }

    /**
     * Appends a field as a list item at the given nesting level (0 for top level fields, 1 for their struct fields),
     * with its type, how it is matched, any aliases and any explanation.
     */
    private static void appendField(Field field, Schema schema, StringBuilder md, int level) {
        md.append("  ".repeat(level)).append("- `").append(field.name()).append("` (").append(typeName(field.type()))
          .append("; ").append(matching(field, schema));
        if (isSearchable(field) && field.matchType() != Field.MatchType.NONE)
            md.append("; match: ").append(field.matchType().name().toLowerCase(Locale.ROOT)); // as in the schema
        md.append(")");
        appendExplanation(field.explanation(), md, level + 1);
        appendInputExpression(field.inputExpression(), md, level + 1);
        md.append("\n");
    }

    /** Appends a lookup of a field with fast map search, which is how it is searched, as a list item under the field. */
    private static void appendLookup(Field field, Field.FastMapSearchFields fastMap, StringBuilder md) {
        md.append("  - `").append(field.name()).append(".").append(fastMap.lookupName()).append("{key}` (lookup; key: ")
          .append(typeName(fastMap.keyType())).append(", value: ").append(typeName(fastMap.valueType())).append(")\n");
    }

    /**
     * Appends an explanation to a list item, indenting any following lines to the given level
     * (1 for top level items) so they stay in the item.
     */
    private static void appendExplanation(Optional<String> explanation, StringBuilder md, int level) {
        if (explanation.isEmpty() || explanation.get().isEmpty()) return;
        md.append(": ").append(explanation.get().strip().replace("\n", "\n" + "  ".repeat(level)));
    }

    private static void appendInputExpression(Optional<String> inputExpression, StringBuilder md, int level) {
        if (inputExpression.isEmpty()) return;
        md.append("\n").append("  ".repeat(level)).append("To search, use `").append(inputExpression.get()).append("`");
    }

    /** Returns the type of an input as it is declared in a rank profile, where an empty tensor type is a double. */
    private static String typeName(RankProfile.InputType type) {
        if ( ! type.declaredString() && type.tensorType().rank() == 0) return "double";
        return type.toString();
    }

    /** Returns whether this is one of the field sets Vespa adds to every schema, such as [document], not a user field set. */
    private static boolean isBuiltIn(FieldSet fieldSet) {
        return fieldSet.name().startsWith("[");
    }

    /**
     * Returns whether this is a field Vespa generates to implement another field, which is queried through that field:
     * the lookup field of a map with fast search (map$lookup) and the z-curve attribute of a position (position_zcurve).
     * These are not in the [internal] field set in all cases.
     */
    private static boolean isInternal(Field field, Schema schema) {
        return isFastMapLookup(field, schema)
               || (field.name().endsWith(Z_CURVE_SUFFIX) && field.type().kind() == Field.Type.Kind.LONG);
    }

    private static boolean isFastMapLookup(Field field, Schema schema) {
        return schema.fields().values().stream()
                     .flatMap(map -> map.fastMapSearches().stream()
                                        .map(fastMap -> FastMapSearch.toLookupFieldName(map.name(), fastMap.lookupName())))
                     .anyMatch(field.name()::equals);
    }

    private static boolean isStreaming(Schema schema, SchemaInfo schemaInfo) {
        return schemaInfo.clusters().values().stream()
                         .anyMatch(cluster -> cluster.isStreaming() && cluster.schemas().contains(schema.name()));
    }

    private static String matching(Field field, Schema schema) {
        if (field.type().kind() == Field.Type.Kind.POSITION && schema.fields().containsKey(field.name() + Z_CURVE_SUFFIX))
            return "attribute"; // implemented by its z-curve attribute
        if (field.isIndex() && field.isAttribute()) return "index, attribute";
        if (field.isIndex()) return "index";
        if (field.isAttribute()) return "attribute";
        boolean hasSearchableStructFields = hasStructFields(field.type()) && structFields(field, schema).anyMatch(SchemaSkill::isSearchable);
        if (hasSearchableStructFields && field.hasFastMapSearch()) return "searchable through its struct fields and by lookup";
        if (hasSearchableStructFields) return "searchable through its struct fields";
        if (field.hasFastMapSearch()) return "searchable by lookup";
        return "not searchable";
    }

    private static boolean isSearchable(Field field) {
        return field.isIndex() || field.isAttribute();
    }

    private static boolean hasStructFields(Field.Type type) {
        return type instanceof Field.MapFieldType
               || type instanceof Field.StructFieldType
               || (type instanceof Field.ArrayFieldType arrayType && arrayType.elementType() instanceof Field.StructFieldType);
    }

    /** Returns the struct fields of the given field, recursively, which schema info names by their full name, e.g. "myStruct.myField". */
    private static Stream<Field> structFields(Field field, Schema schema) {
        return schema.fields().values().stream().filter(structField -> structField.name().startsWith(field.name() + "."));
    }

    /** Returns whether this is a field inside a struct or map field, such as "myStruct.myField" or "myMap.key". */
    private static boolean isStructField(Field field, Schema schema) {
        int lastDot = field.name().lastIndexOf('.');
        if (lastDot < 0) return false;
        Field parent = schema.fields().get(field.name().substring(0, lastDot));
        return parent != null && hasStructFields(parent.type());
    }

    private static String typeName(Field.Type type) {
        if (type instanceof Field.TensorFieldType tensorType)
            return tensorType.tensorType().toString();
        if (type instanceof Field.MapFieldType mapType)
            return "map<" + typeName(mapType.keyType()) + ", " + typeName(mapType.valueType()) + ">";
        if (type instanceof Field.ArrayFieldType arrayType)
            return "array<" + typeName(arrayType.elementType()) + ">";
        if (type instanceof Field.WeightedSetFieldType weightedSetType)
            return "weightedset<" + typeName(weightedSetType.elementType()) + ">";
        if (type instanceof Field.ReferenceFieldType referenceType)
            return "reference<" + referenceType.documentType() + ">";
        if (type instanceof Field.StructFieldType structType)
            return "struct " + structType.name();
        if (type instanceof Field.AnnotationReferenceFieldType annotationReferenceType)
            return "annotationreference<" + annotationReferenceType.annotationType() + ">";
        return type.kind().name().toLowerCase(Locale.ROOT);
    }

    private static String code(String text) { return "`" + text + "`"; }

}
