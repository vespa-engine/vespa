// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.skill;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.FieldSet;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An agent skill describing the schemas of this application, telling agents what they can query and how,
 * including the explanations written in the schemas. Creates an overview (the SKILL.md document) listing the schemas,
 * and a document per schema with its fields, field sets and rank profiles, as markdown.
 *
 * @author ingrid
 */
public class SchemaSkill extends AbstractComponent {

    static final String name = "vespa-application-schemas";
    private static final String zCurveSuffix = "_zcurve";

    private final SchemaInfo schemaInfo;
    private final Map<String, String> schemaDocuments = new LinkedHashMap<>();

    @Inject
    public SchemaSkill(SchemaInfo schemaInfo) {
        this.schemaInfo = schemaInfo;
        schemaInfo.schemas().forEach((name, schema) -> schemaDocuments.put(name, renderSchema(schema, schemaInfo)));
    }

    /**
     * Returns the overview of the schemas, which is the SKILL.md of the skill.
     *
     * @param searchPaths the paths to query this application at, such as /search/
     * @param schemaLink returns where to find the document of the schema with the given name
     */
    public String overview(List<String> searchPaths, Function<String, String> schemaLink) {
        var md = new StringBuilder();
        md.append("---\n")
          .append("name: ").append(name).append("\n")
          .append("description: The schemas, fields and rank profiles of this Vespa application, ")
          .append("with notes from its authors. Use this when writing queries against this application.\n")
          .append("---\n\n");
        md.append("# Querying this application\n\n")
          .append("Query this application with YQL")
          .append(searchPaths.isEmpty() ? "" : " at " + searchPaths.stream().map(SchemaSkill::code).collect(Collectors.joining(" or ")))
          .append(". ")
          .append("For general Vespa query syntax, use the Vespa skills (`vespa skills install`).\n");
        if ( ! schemaInfo.schemas().isEmpty())
            md.append("\nEach schema below links to its fields, field sets and rank profiles. ")
              .append("Read it before querying the schema.\n");
        for (Schema schema : schemaInfo.schemas().values()) {
            md.append("\n## Schema `").append(schema.name()).append("`\n");
            appendSchemaInfo(schema, schemaInfo, md);
            md.append("\nDetails: `").append(schemaLink.apply(schema.name())).append("`\n");
        }
        return md.toString();
    }

    /** Returns the document describing the given schema, or empty if there is no schema with this name. */
    public Optional<String> schema(String name) { return Optional.ofNullable(schemaDocuments.get(name)); }

    private static String renderSchema(Schema schema, SchemaInfo schemaInfo) {
        var md = new StringBuilder();
        md.append("# Schema `").append(schema.name()).append("`\n");
        appendSchemaInfo(schema, schemaInfo, md);

        List<Field> fields = schema.fields().values().stream().filter(field -> ! isInternal(field)).toList();
        if ( ! fields.isEmpty()) {
            md.append("\n## Fields\n\n");
            for (Field field : fields) {
                md.append("- `").append(field.name()).append("` (").append(typeName(field.type()))
                  .append("; ").append(matching(field, schema));
                if ( ! field.aliases().isEmpty())
                    md.append("; aliases: ").append(field.aliases().stream().map(SchemaSkill::code).collect(Collectors.joining(", ")));
                md.append(")");
                appendExplanation(field.explanation(), md, 1);
                md.append("\n");
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
     * Appends an explanation to a list item at the given nesting level (1 for top level items),
     * indenting any following lines so they stay in the item.
     */
    private static void appendExplanation(Optional<String> explanation, StringBuilder md, int level) {
        explanation.map(String::strip)
                   .filter(text -> ! text.isEmpty())
                   .ifPresent(text -> md.append(": ").append(text.replace("\n", "\n" + "  ".repeat(level))));
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
     * These are not in the [internal] field set in all cases, and that set also contains fields which can be queried,
     * such as the parts of a uri field (url.hostname).
     */
    private static boolean isInternal(Field field) {
        return field.name().contains("$")
               || (field.name().endsWith(zCurveSuffix) && field.type().kind() == Field.Type.Kind.LONG);
    }

    private static boolean isStreaming(Schema schema, SchemaInfo schemaInfo) {
        return schemaInfo.clusters().values().stream()
                         .anyMatch(cluster -> cluster.isStreaming() && cluster.schemas().contains(schema.name()));
    }

    private static String matching(Field field, Schema schema) {
        if (field.type().kind() == Field.Type.Kind.POSITION && schema.fields().containsKey(field.name() + zCurveSuffix))
            return "attribute"; // implemented by its z-curve attribute
        if (field.isIndex() && field.isAttribute()) return "index, attribute";
        if (field.isIndex()) return "index";
        if (field.isAttribute()) return "attribute";
        // Struct fields with attributes or indexes are not in schema info yet, so we can't tell whether there are any
        if (hasStructFields(field.type())) return "searchable only through its struct fields, if any";
        return "not searchable";
    }

    private static boolean hasStructFields(Field.Type type) {
        return type instanceof Field.MapFieldType
               || type instanceof Field.StructFieldType
               || (type instanceof Field.ArrayFieldType arrayType && arrayType.elementType() instanceof Field.StructFieldType);
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
