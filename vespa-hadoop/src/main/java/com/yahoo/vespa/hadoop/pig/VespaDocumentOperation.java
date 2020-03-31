// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.pig;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.vespa.hadoop.mapreduce.util.TupleTools;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.pig.EvalFunc;
import org.apache.pig.PigWarning;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DataByteArray;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.tools.pigstats.PigStatusReporter;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.joda.time.DateTime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * A Pig UDF to convert simple Pig types into a valid Vespa JSON document format.
 *
 * @author lesters
 */
public class VespaDocumentOperation extends EvalFunc<String> {

    public enum Operation {
        DOCUMENT,
        PUT,
        ID,
        REMOVE,
        UPDATE;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public static Operation fromString(String text) {
            for (Operation op : Operation.values()) {
                if (op.toString().equalsIgnoreCase(text)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown operation: " + text);
        }

        public static boolean valid(String text) {
            for (Operation op : Operation.values()) {
                if (op.toString().equalsIgnoreCase(text)) {
                    return true;
                }
            }
            return false;
        }

    }

    private static final String PROPERTY_CREATE_IF_NON_EXISTENT = "create-if-non-existent";
    private static final String PROPERTY_ID_TEMPLATE = "docid";
    private static final String PROPERTY_OPERATION = "operation";
    private static final String BAG_AS_MAP_FIELDS = "bag-as-map-fields";
    private static final String SIMPLE_ARRAY_FIELDS = "simple-array-fields";
    private static final String SIMPLE_OBJECT_FIELDS = "simple-object-fields";
    private static final String CREATE_TENSOR_FIELDS = "create-tensor-fields";
    private static final String REMOVE_TENSOR_FIELDS = "remove-tensor-fields";
    private static final String UPDATE_TENSOR_FIELDS = "update-tensor-fields";
    private static final String REMOVE_MAP_FIELDS = "remove-map-fields";
    private static final String UPDATE_MAP_FIELDS = "update-map-fields";
    private static final String EXCLUDE_FIELDS = "exclude-fields";
    private static final String TESTSET_CONDITION = "condition";
    private static final String PARTIAL_UPDATE_ASSIGN = "assign";
    private static final String PARTIAL_UPDATE_ADD = "add";
    private static final String PARTIAL_UPDATE_REMOVE = "remove";

    private static Map<String, String> mapPartialOperationMap;
    static {
        mapPartialOperationMap = new HashMap<>();
        mapPartialOperationMap.put(REMOVE_MAP_FIELDS, PARTIAL_UPDATE_REMOVE);
        mapPartialOperationMap.put(UPDATE_MAP_FIELDS, PARTIAL_UPDATE_ASSIGN);
    }

    private static Map<String, String> partialOperationMap;
    static {
        partialOperationMap = new HashMap<>();
        partialOperationMap.put(REMOVE_TENSOR_FIELDS, PARTIAL_UPDATE_REMOVE);
        partialOperationMap.put(UPDATE_TENSOR_FIELDS, PARTIAL_UPDATE_ADD);
        partialOperationMap.put(REMOVE_MAP_FIELDS, PARTIAL_UPDATE_REMOVE);
        partialOperationMap.put(UPDATE_MAP_FIELDS, PARTIAL_UPDATE_ASSIGN);
    }

    private final String template;
    private final Operation operation;
    private final Properties properties;
    private PigStatusReporter statusReporter;

    public VespaDocumentOperation(String... params) {
        statusReporter = PigStatusReporter.getInstance();
        if(statusReporter != null){
            statusReporter.incrCounter("Vespa Document Operation Counters","Document operation ok",0);
            statusReporter.incrCounter("Vespa Document Operation Counters","Document operation failed",0);
        }
        properties = VespaConfiguration.loadProperties(params);
        template = properties.getProperty(PROPERTY_ID_TEMPLATE);
        operation = Operation.fromString(properties.getProperty(PROPERTY_OPERATION, "put"));
    }

    @Override
    public String exec(Tuple tuple) throws IOException {
        if (tuple == null || tuple.size() == 0) {
            if(statusReporter != null) {
                statusReporter.incrCounter("Vespa Document Operation Counters", "Document operation failed", 1);
            }
            return null;
        }
        if (template == null || template.length() == 0) {
            if(statusReporter != null) {
                statusReporter.incrCounter("Vespa Document Operation Counters", "Document operation failed", 1);
            }
            warn("No valid document id template found. Skipping.", PigWarning.UDF_WARNING_1);
            return null;
        }
        if (operation == null) {
            if(statusReporter != null) {
                statusReporter.incrCounter("Vespa Document Operation Counters", "Document operation failed", 1);
            }
            warn("No valid operation found. Skipping.", PigWarning.UDF_WARNING_1);
            return null;
        }

        String json = null;

        try {
            if (reporter != null) {
                reporter.progress();
            }

            Schema inputSchema = getInputSchema();
            Map<String, Object> fields = TupleTools.tupleMap(inputSchema, tuple);
            String docId = TupleTools.toString(fields, template);
            // create json
            json = create(operation, docId, fields, properties, inputSchema);
            if (json == null || json.length() == 0) {
                warn("No valid document operation could be created.", PigWarning.UDF_WARNING_1);
                return null;
            }


        } catch (Exception e) {
            if (statusReporter != null) {
                statusReporter.incrCounter("Vespa Document Operation Counters","Document operation failed",1);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Caught exception processing input row: \n");
            sb.append(tuple.toString());
            sb.append("\nException: ");
            sb.append(ExceptionUtils.getStackTrace(e));
            warn(sb.toString(), PigWarning.UDF_WARNING_1);
            return null;
        }
        if (statusReporter != null) {
            statusReporter.incrCounter("Vespa Document Operation Counters","Document operation ok",1);
        }
        return json;
    }


    /**
     * Create a JSON Vespa document operation given the supplied fields,
     * operation and document id template.
     *
     * @param op        Operation (put, remove, update)
     * @param docId     Document id
     * @param fields    Fields to put in document operation
     * @return          A valid JSON Vespa document operation
     * @throws IOException ...
     */
    public static String create(Operation op, String docId, Map<String, Object> fields, Properties properties,
            Schema schema) throws IOException {
        if (op == null) {
            return null;
        }
        if (docId == null || docId.length() == 0) {
            return null;
        }
        if (fields.isEmpty()) {
            return null;
        }

        // create json format
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator g = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);
        g.writeStartObject();

        g.writeStringField(op.toString(), docId);

        boolean createIfNonExistent = Boolean.parseBoolean(properties.getProperty(PROPERTY_CREATE_IF_NON_EXISTENT, "false"));
        if (op == Operation.UPDATE && createIfNonExistent) {
            writeField("create", true, DataType.BOOLEAN, g, properties, schema, op, 0);
        }
        String testSetConditionTemplate = properties.getProperty(TESTSET_CONDITION);
        if (testSetConditionTemplate != null) {
            String testSetCondition = TupleTools.toString(fields, testSetConditionTemplate);
            writeField(TESTSET_CONDITION, testSetCondition, DataType.CHARARRAY, g, properties, schema, op, 0);
        }
        if (op != Operation.REMOVE) {
            writeField("fields", fields, DataType.MAP, g, properties, schema, op, 0);
        }

        g.writeEndObject();
        g.close();

        return out.toString();
    }

    private static String getPartialOperation(Map<String, String> operationMap, String name, Properties properties) {
        // This function checks if the property of the name falls into the map provided
        // if yes, return the desired operation. if no, return null
        // for example, input:
        // operationMap map{"update-map-fields":"assign","remove-map-fields":"remove"}
        // name date
        // properties "update-map-fields":"date,month"
        // output: assign
        for (String label: operationMap.keySet()) {
            if (properties.getProperty(label) != null) {
                String[] p = properties.getProperty(label).split(",");
                if (Arrays.asList(p).contains(name)) {
                    return operationMap.get(label);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void writeField(String name, Object value, Byte type, JsonGenerator g, Properties properties, Schema schema, Operation op, int depth) throws IOException {
        if (shouldWriteField(name, properties, depth)) {
            String operation = getPartialOperation(mapPartialOperationMap, name, properties);
            // check if the name has the property update-map-fields/remove-map-fields
            // if yes, we need special treatments here as we need to loop through the tuple
            // be aware the the operation here is not vespa operation such as "put" and "update"
            // operation here are the field name we wish use to such as "assign" and "remove"
            if (operation != null) {
                writePartialUpdateAndRemoveMap(name ,value, g, properties, schema, op, depth, operation);
            }else{
                g.writeFieldName(name);
                if (shouldWritePartialUpdate(op, depth)) {
                    writePartialUpdate(value, type, g, name, properties, schema, op, depth);
                } else {
                    writeValue(value, type, g, name, properties, schema, op, depth);
                }
            }

        }
    }

    private static void writePartialUpdateAndRemoveMap(String name, Object value, JsonGenerator g, Properties properties, Schema schema, Operation op, int depth, String operation) throws IOException {
        schema = (schema != null) ? schema.getField(0).schema : null;
        // extract the key of map and keys in map for writing json when partial updating maps
        Schema valueSchema = (schema != null) ? schema.getField(1).schema : null;
        // data format  { ( key; id, value: (abc,123,(123234,bbaa))) }
        // the first element of each tuple in the bag will be the map to update
        // the second element of each tuple in the bag will be the new value of the map
        DataBag bag = (DataBag) value;
        for (Tuple element : bag) {
            if (element.size() != 2) {
                continue;
            }
            String k = (String) element.get(0);
            Object v = element.get(1);
            Byte t = DataType.findType(v);
            if (t == DataType.TUPLE) {
                g.writeFieldName(name + "{" + k + "}");
                if (operation.equals(PARTIAL_UPDATE_REMOVE)) {
                    g.writeStartObject();
                    g.writeFieldName(PARTIAL_UPDATE_REMOVE);
                    g.writeNumber(0);
                    g.writeEndObject();
                }else{
                    writePartialUpdate(v, t, g, name, properties, valueSchema, op, depth);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, Byte type, JsonGenerator g, String name, Properties properties, Schema schema, Operation op, int depth) throws IOException {
        switch (type) {
            case DataType.UNKNOWN:
                break;
            case DataType.NULL:
                g.writeNull();
                break;
            case DataType.BOOLEAN:
                g.writeBoolean((boolean) value);
                break;
            case DataType.INTEGER:
                g.writeNumber((int) value);
                break;
            case DataType.LONG:
                g.writeNumber((long) value);
                break;
            case DataType.FLOAT:
                g.writeNumber((float) value);
                break;
            case DataType.DOUBLE:
                g.writeNumber((double) value);
                break;
            case DataType.DATETIME:
                g.writeNumber(((DateTime) value).getMillis());
                break;
            case DataType.BYTEARRAY:
                DataByteArray bytes = (DataByteArray) value;
                String raw = Base64.getEncoder().encodeToString(bytes.get());
                g.writeString(raw);
                break;
            case DataType.CHARARRAY:
                g.writeString((String) value);
                break;
            case DataType.BIGINTEGER:
                g.writeNumber((BigInteger) value);
                break;
            case DataType.BIGDECIMAL:
                g.writeNumber((BigDecimal) value);
                break;
            case DataType.MAP:
                g.writeStartObject();
                Map<Object, Object> map = (Map<Object, Object>) value;
                if (shouldCreateTensor(map, name, properties)) {
                    if(isRemoveTensor(name,properties)){
                        writeRemoveTensor(map, g);
                    }else{
                        writeTensor(map, g);
                    }
                } else {
                    for (Map.Entry<Object, Object> entry : map.entrySet()) {
                        String k = entry.getKey().toString();
                        Object v = entry.getValue();
                        Byte   t = DataType.findType(v);
                        Schema fieldSchema = (schema != null) ? schema.getField(k).schema : null;
                        writeField(k, v, t, g, properties, fieldSchema, op, depth+1);
                    }
                }
                g.writeEndObject();
                break;
            case DataType.TUPLE:
                Tuple tuple = (Tuple) value;
                if (shouldWriteTupleAsMap(name, properties)) {
                    Map<String, Object> fields = TupleTools.tupleMap(schema, tuple);
                    writeValue(fields, DataType.MAP, g, name, properties, schema, op, depth);
                } else {
                    boolean writeStartArray = shouldWriteTupleStart(tuple, name, properties);
                    if (writeStartArray) {
                        g.writeStartArray();
                    }
                    for (Object v : tuple) {
                        writeValue(v, DataType.findType(v), g, name, properties, schema, op, depth);
                    }
                    if (writeStartArray) {
                        g.writeEndArray();
                    }
                }
                break;
            case DataType.BAG:
                DataBag bag = (DataBag) value;
                // get the schema of the tuple in bag
                schema = (schema != null) ? schema.getField(0).schema : null;
                if (shouldWriteBagAsMap(name, properties)) {
                    // when treating bag as map, the schema of bag should be {(key, val)....}
                    // the size of tuple in bag should be 2. 1st one is key. 2nd one is val.
                    Schema valueSchema = (schema != null) ? schema.getField(1).schema : null;

                    g.writeStartObject();
                    for (Tuple element : bag) {
                        if (element.size() != 2) {
                            continue;
                        }
                        String k = (String) element.get(0);
                        Object v = element.get(1);
                        Byte t = DataType.findType(v);
                        if (t == DataType.TUPLE) {
                            Map<String, Object> fields = TupleTools.tupleMap(valueSchema, (Tuple) v);
                            writeField(k, fields, DataType.MAP, g, properties, valueSchema, op, depth+1);
                        } else {
                            writeField(k, v, t, g, properties, valueSchema, op, depth+1);
                        }
                    }
                    g.writeEndObject();
                } else {
                    g.writeStartArray();
                    for (Tuple t : bag) {
                        writeValue(t, DataType.TUPLE, g, name, properties, schema, op, depth);
                    }
                    g.writeEndArray();
                }
                break;
        }

    }

    private static boolean shouldWritePartialUpdate(Operation op, int depth) {
        return op == Operation.UPDATE && depth == 1;
    }

    private static void writePartialUpdate(Object value, Byte type, JsonGenerator g, String name, Properties properties, Schema schema, Operation op, int depth) throws IOException {
        g.writeStartObject();
        // here we check if the operation falls into the four partial operations we do on map/tensor structure
        // if no, we assume it's a update on the whole document and we write assign here
        // if yes, we write the desired operation here
        String operation = getPartialOperation(partialOperationMap, name, properties);
        if (operation != null) {
            g.writeFieldName(operation);
        }else{
            g.writeFieldName(PARTIAL_UPDATE_ASSIGN);
        }
        writeValue(value, type, g, name, properties, schema, op, depth);
        g.writeEndObject();
    }

    private static boolean isPartialOperation(String label, String name, Properties properties) {
        // when dealing with partial update operations, write the desired operation
        // writeFieldName decides if a field name should be written when checking
        boolean isPartialOperation = false;
        if (properties.getProperty(label) != null) {
            String[] p = properties.getProperty(label).split(",");
            if (Arrays.asList(p).contains(name)) {
                isPartialOperation = true;
            }
        }
        return isPartialOperation;
    }

    private static boolean shouldWriteTupleStart(Tuple tuple, String name, Properties properties) {
        if (tuple.size() > 1 || properties == null) {
            return true;
        }
        String simpleArrayFields = properties.getProperty(SIMPLE_ARRAY_FIELDS);
        if (simpleArrayFields == null) {
            return true;
        }
        if (simpleArrayFields.equals("*")) {
            return false;
        }
        String[] fields = simpleArrayFields.split(",");
        for (String field : fields) {
            if (field.trim().equalsIgnoreCase(name)) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldWriteTupleAsMap(String name, Properties properties) {
        // include UPDATE_MAP_FIELDS here because when updating the map
        // the second element in each tuple should be written as a map
        if (properties == null) {
            return false;
        }
        String addBagAsMapFields = properties.getProperty(UPDATE_MAP_FIELDS);
        String simpleObjectFields = properties.getProperty(SIMPLE_OBJECT_FIELDS);
        if (simpleObjectFields == null && addBagAsMapFields == null) {
            return false;
        }
        if (addBagAsMapFields != null){
            if (addBagAsMapFields.equals("*")) {
                return true;
            }
            String[] fields = addBagAsMapFields.split(",");
            for (String field : fields) {
                if (field.trim().equalsIgnoreCase(name)) {
                    return true;
                }
            }

        }
        if(simpleObjectFields != null){
                if (simpleObjectFields.equals("*")) {
                return true;
            }
            String[] fields = simpleObjectFields.split(",");
            for (String field : fields) {
                if (field.trim().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldWriteBagAsMap(String name, Properties properties) {
        if (properties == null) {
            return false;
        }
        String bagAsMapFields = properties.getProperty(BAG_AS_MAP_FIELDS);
        if (bagAsMapFields == null) {
            return false;
        }
        if (bagAsMapFields.equals("*")) {
            return true;
        }
        String[] fields = bagAsMapFields.split(",");
        for (String field : fields) {
            if (field.trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldCreateTensor(Map<Object, Object> map, String name, Properties properties) {
        if (properties == null) {
            return false;
        }
        String createTensorFields = properties.getProperty(CREATE_TENSOR_FIELDS);
        String addTensorFields = properties.getProperty(UPDATE_TENSOR_FIELDS);
        String removeTensorFields = properties.getProperty(REMOVE_TENSOR_FIELDS);

        if (createTensorFields == null && addTensorFields == null && removeTensorFields == null) {
            return false;
        }
        String[] fields;
        if (createTensorFields != null) {
            fields = createTensorFields.split(",");
            for (String field : fields) {
                if (field.trim().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        if (addTensorFields != null) {
            fields = addTensorFields.split(",");
            for (String field : fields) {
                if (field.trim().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        if (removeTensorFields != null) {
            fields = removeTensorFields.split(",");
            for (String field : fields) {
                if (field.trim().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRemoveTensor(String name, Properties properties){
        if (properties == null) {
            return false;
        }
        String removeTensorFields = properties.getProperty(REMOVE_TENSOR_FIELDS);
        if (removeTensorFields == null) {
            return false;
        }
        String[] fields = removeTensorFields.split(",");
        for (String field : fields) {
            if (field.trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldWriteField(String name, Properties properties, int depth) {
        if (properties == null || depth != 1) {
            return true;
        }
        String excludeFields = properties.getProperty(EXCLUDE_FIELDS);
        if (excludeFields == null) {
            return true;
        }
        String[] fields = excludeFields.split(",");
        for (String field : fields) {
            if (field.trim().equalsIgnoreCase(name)) {
                return false;
            }
        }
        return true;
    }

    private static void writeTensor(Map<Object, Object> map, JsonGenerator g) throws IOException {
        g.writeFieldName("cells");
        g.writeStartArray();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            String k = entry.getKey().toString();
            Double v = Double.parseDouble(entry.getValue().toString());

            g.writeStartObject();

            // Write address
            g.writeFieldName("address");
            g.writeStartObject();

            String[] dimensions = k.split(",");
            for (String dimension : dimensions) {
                if (dimension == null || dimension.isEmpty()) {
                    continue;
                }
                String[] address = dimension.split(":");
                if (address.length != 2) {
                    throw new IllegalArgumentException("Malformed cell address: " + dimension);
                }
                String dim = address[0];
                String label = address[1];
                if (dim == null || label == null || dim.isEmpty() || label.isEmpty()) {
                    throw new IllegalArgumentException("Malformed cell address: " + dimension);
                }
                g.writeFieldName(dim.trim());
                g.writeString(label.trim());
            }
            g.writeEndObject();

            // Write value
            g.writeFieldName("value");
            g.writeNumber(v);

            g.writeEndObject();
        }
        g.writeEndArray();
    }

    private static void writeRemoveTensor(Map<Object, Object> map, JsonGenerator g) throws IOException {
        g.writeFieldName("addresses");
        g.writeStartArray();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            String k = entry.getKey().toString();
            String[] dimensions = k.split(",");
            for (String dimension : dimensions) {
                g.writeStartObject();
                if (dimension == null || dimension.isEmpty()) {
                    continue;
                }
                String[] address = dimension.split(":");
                if (address.length != 2) {
                    throw new IllegalArgumentException("Malformed cell address: " + dimension);
                }
                String dim = address[0];
                String label = address[1];
                if (dim == null || label == null || dim.isEmpty() || label.isEmpty()) {
                    throw new IllegalArgumentException("Malformed cell address: " + dimension);
                }
                g.writeFieldName(dim.trim());
                g.writeString(label.trim());
                g.writeEndObject();
                // Write address
            }
        }
        g.writeEndArray();
    }
}
