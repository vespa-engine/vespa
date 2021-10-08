// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.collections.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The rank settings of a field used for native rank features.
 *
 * @author geirst
 */
public class FieldRankSettings {

    private static final Logger logger = Logger.getLogger(FieldRankSettings.class.getName());

    private String fieldName;

    private final Map<String, NativeTable> tables = new LinkedHashMap<>();

    public FieldRankSettings(String fieldName) {
        this.fieldName = fieldName;
    }

    public void addTable(NativeTable table) {
        NativeTable existing = tables.get(table.getType().getName());
        if (existing != null) {
            logger.info("Using already specified rank table " + existing + " for field " + fieldName + ", not " + table);
            return;
        }
        tables.put(table.getType().getName(), table);
    }

    public static boolean isIndexFieldTable(NativeTable table) {
        return isFieldMatchTable(table) || isProximityTable(table);
    }

    public static boolean isAttributeFieldTable(NativeTable table) {
        return isAttributeMatchTable(table);
    }

    private static boolean isFieldMatchTable(NativeTable table) {
        return (table.getType().equals(NativeTable.Type.FIRST_OCCURRENCE) ||
                table.getType().equals(NativeTable.Type.OCCURRENCE_COUNT));
    }

    private static boolean isAttributeMatchTable(NativeTable table) {
        return (table.getType().equals(NativeTable.Type.WEIGHT));
    }

    private static boolean isProximityTable(NativeTable table) {
        return (table.getType().equals(NativeTable.Type.PROXIMITY) ||
                table.getType().equals(NativeTable.Type.REVERSE_PROXIMITY));
    }

    public List<Pair<String, String>> deriveRankProperties() {
        List<Pair<String, String>> properties = new ArrayList<>();
        for (Iterator<NativeTable> i = tables.values().iterator(); i.hasNext();) {
            NativeTable table = i.next();
            if (isFieldMatchTable(table))
                properties.add(new Pair<>("nativeFieldMatch." + table.getType().getName() + "." + fieldName, table.getName()));
            if (isAttributeMatchTable(table))
                properties.add(new Pair<>("nativeAttributeMatch." + table.getType().getName() + "." + fieldName, table.getName()));
            if (isProximityTable(table))
                properties.add(new Pair<>("nativeProximity." + table.getType().getName() + "." + fieldName, table.getName()));
        }
        return properties;
    }

    public String toString() {
        return "rank settings of field " + fieldName;
    }

}
