// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.mapreduce.util;

import org.apache.pig.ResourceSchema;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.schema.Schema;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TupleTools {

    private static final Pattern pattern = Pattern.compile("<([\\w]+)>");

    public static Map<String, Object> tupleMap(Schema schema, Tuple tuple) throws IOException {
        Map<String, Object> tupleMap = new HashMap<>((int)Math.ceil(tuple.size() / 0.75) + 1);
        List<Schema.FieldSchema> schemas = schema.getFields();
        for (int i = 0; i < schemas.size(); i++) {
            Schema.FieldSchema field = schemas.get(i);
            String alias = field.alias;
            Object value = tuple.get(i);
            if (value != null) {
                tupleMap.put(alias, value);
            }
        }
        return tupleMap;
    }

    public static Map<String, Object> tupleMap(ResourceSchema schema, Tuple tuple) throws IOException {
        Map<String, Object> tupleMap = new HashMap<>((int)Math.ceil(tuple.size() / 0.75) + 1);
        ResourceSchema.ResourceFieldSchema[] schemas = schema.getFields();
        for (int i = 0; i < schemas.length; i++) {
            ResourceSchema.ResourceFieldSchema field = schemas[i];
            String alias = field.getName();
            Object value = tuple.get(i);
            if (value != null) {
                tupleMap.put(alias, value);
            }
        }
        return tupleMap;
    }

    public static String toString(Schema schema, Tuple tuple, String template) throws IOException {
        return toString(tupleMap(schema, tuple), template);
    }

    public static String toString(Map<String,Object> fields, String template) {
        if (template == null || template.length() == 0) {
            return template;
        }
        if (fields == null || fields.size() == 0) {
            return template;
        }

        Matcher m = pattern.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            Object value = fields.get(m.group(1));
            String replacement = value != null ? value.toString() : m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

}
