package com.yahoo.schema.processing;

import com.yahoo.document.DataType;
import com.yahoo.vespa.documentmodel.SummaryField;

/**
 * This class contains utils used when handling summary fields with dynamic transforms during processing and deriving.
 *
 * Originally (before Vespa 8.52), dynamic transforms where only supported for string fields.
 * Due to legacy functionality in the backend docsum framework,
 * such summary fields are in some cases added as extra document fields and populated in indexing scripts.
 * This is something we want to avoid in the future, but it might not be entirely possible before Vespa 9.
 *
 * With the introduction of dynamic transform for array of string fields,
 * we move in the right direction and avoid adding extra document fields with indexing script population for this type.
 * Instead, we configure the dynamic transform in the backend to use the original source field directly.
 *
 * See SummaryTransform.isDynamic() for which transforms this applies to.
 */
public class DynamicSummaryTransformUtils {

    public static boolean hasSupportedType(SummaryField field) {
        return isSupportedType(field.getDataType());
    }

    public static boolean isSupportedType(DataType type) {
        return isOriginalSupportedType(type) || isNewSupportedType(type);
    }

    private static boolean isOriginalSupportedType(DataType type) {
        return (type == DataType.STRING) ||
                (type == DataType.URI);
    }

    private static boolean isNewSupportedType(DataType type) {
        return (type.equals(DataType.getArray(DataType.STRING)));
    }

    /**
     * Whether a summary field must be populated by the source field with the given type in an indexing script.
     */
    public static boolean summaryFieldIsPopulatedBySourceField(DataType sourceFieldType) {
        return isOriginalSupportedType(sourceFieldType);
    }

    /**
     * Whether a summary field is required as an extra field in the document type.
     */
    public static boolean summaryFieldIsRequiredInDocumentType(SummaryField summaryField) {
        return summaryFieldIsPopulatedBySourceField(summaryField.getDataType());
    }

    public static String getSource(SummaryField summaryField) {
        // Summary fields with the original supported type is always present in the document type,
        // and we must use that field as source at run-time.
        return isOriginalSupportedType(summaryField.getDataType()) ?
                summaryField.getName() : summaryField.getSingleSource();
    }
}
