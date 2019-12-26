// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.pig;

import com.yahoo.vespa.hadoop.mapreduce.VespaOutputFormat;
import com.yahoo.vespa.hadoop.mapreduce.util.TupleTools;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.pig.ResourceSchema;
import org.apache.pig.StoreFunc;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.util.UDFContext;

import java.io.*;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

/**
 * A small Pig UDF wrapper around the Vespa http client for
 * feeding data into a Vespa endpoint.
 *
 * @author lesters
 */
@SuppressWarnings("rawtypes")
public class VespaStorage extends StoreFunc {

    private final boolean createDocOp;
    private final String template;
    private final VespaDocumentOperation.Operation operation;

    private String signature = null;
    private RecordWriter recordWriter = null;
    private ResourceSchema resourceSchema = null;

    private static final String PROPERTY_CREATE_DOC_OP = "create-document-operation";
    private static final String PROPERTY_ID_TEMPLATE = "docid";
    private static final String PROPERTY_OPERATION = "operation";
    private static final String PROPERTY_RESOURCE_SCHEMA = "resource_schema";

    Properties properties = new Properties();

    public VespaStorage() {
        createDocOp = false;
        template = null;
        operation = null;
    }

    public VespaStorage(String... params) {
        properties = VespaConfiguration.loadProperties(params);
        createDocOp = Boolean.parseBoolean(properties.getProperty(PROPERTY_CREATE_DOC_OP, "false"));
        operation = VespaDocumentOperation.Operation.fromString(properties.getProperty(PROPERTY_OPERATION, "put"));
        template = properties.getProperty(PROPERTY_ID_TEMPLATE);
    }


    @Override
    public OutputFormat getOutputFormat() throws IOException {
        return new VespaOutputFormat(properties);
    }


    @Override
    public void setStoreLocation(String endpoint, Job job) throws IOException {
        properties.setProperty(VespaConfiguration.ENDPOINT, endpoint);
    }


    @Override
    public void prepareToWrite(RecordWriter recordWriter) throws IOException {
        this.recordWriter = recordWriter;
        this.resourceSchema = getResourceSchema();
    }


    @SuppressWarnings("unchecked")
    @Override
    public void putNext(Tuple tuple) throws IOException {
        if (tuple == null || tuple.size() == 0) {
            return;
        }

        String data = null;
        if (createDocOp) {
            data = createDocumentOperation(tuple);
        } else if (!tuple.isNull(0)) {
            data = tuple.get(0).toString(); // assume single field with correctly formatted doc op.
        }

        if (data == null || data.length() == 0) {
            return;
        }

        try {
            recordWriter.write(0, data);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }


    @Override
    public void checkSchema(ResourceSchema resourceSchema) throws IOException {
        setResourceSchema(resourceSchema);
    }


    @Override
    public String relToAbsPathForStoreLocation(String endpoint, Path path) throws IOException {
        return endpoint;
    }


    @Override
    public void setStoreFuncUDFContextSignature(String s) {
        this.signature = s;
    }


    @Override
    public void cleanupOnFailure(String s, Job job) throws IOException {
    }


    @Override
    public void cleanupOnSuccess(String s, Job job) throws IOException {
    }


    private ResourceSchema getResourceSchema() throws IOException {
        Properties properties = getUDFProperties();
        return base64Deserialize(properties.getProperty(PROPERTY_RESOURCE_SCHEMA));
    }


    private void setResourceSchema(ResourceSchema schema) throws IOException {
        Properties properties = getUDFProperties();
        if (properties.getProperty(PROPERTY_RESOURCE_SCHEMA) == null) {
            properties.setProperty(PROPERTY_RESOURCE_SCHEMA, base64Serialize(schema));
        }
    }


    private Properties getUDFProperties() {
        String[] context = { signature };
        return UDFContext.getUDFContext().getUDFProperties(getClass(), context);
    }


    private String createDocumentOperation(Tuple tuple) throws IOException {
        if (tuple == null || tuple.size() == 0) {
            return null;
        }
        if (resourceSchema == null) {
            return null;
        }

        Map<String, Object> fields = TupleTools.tupleMap(resourceSchema, tuple);
        String docId = TupleTools.toString(fields, template);

        Schema schema = Schema.getPigSchema(resourceSchema);
        return VespaDocumentOperation.create(operation, docId, fields, properties, schema);
    }


    public static String base64Serialize(Object o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(o);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }


    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T base64Deserialize(String s) throws IOException {
        Object ret;
        byte[] data = Base64.getDecoder().decode(s);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            ret = ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        return (T) ret;
    }

}
