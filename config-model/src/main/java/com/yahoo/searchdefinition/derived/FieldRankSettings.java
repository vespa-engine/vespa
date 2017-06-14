// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import java.util.Iterator;
import java.util.LinkedHashMap;
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

    public Map<String,String> deriveRankProperties(int part) {
        Map<String,String> ret = new LinkedHashMap<>();
        int i = part;
        for (Iterator<NativeTable> itr = tables.values().iterator(); itr.hasNext(); ++i) {
            NativeTable table = itr.next();
            if (isFieldMatchTable(table)) {
                ret.put("nativeFieldMatch." + table.getType().getName() + "." + fieldName + ".part" + i, table.getName());
            }
            if (isAttributeMatchTable(table)) {
                ret.put("nativeAttributeMatch." + table.getType().getName() + "." + fieldName + ".part" + i, table.getName());
            }
            if (isProximityTable(table)) {
                ret.put("nativeProximity." + table.getType().getName() + "." + fieldName + ".part" + i, table.getName());
            }
        }
        return ret;
    }

    public String toString() {
        return "rank settings of field " + fieldName;
    }

}
