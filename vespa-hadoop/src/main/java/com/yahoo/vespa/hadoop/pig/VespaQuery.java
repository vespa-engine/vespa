// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.pig;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaHttpClient;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaQuerySchema;
import com.yahoo.vespa.hadoop.mapreduce.util.TupleTools;
import org.apache.pig.EvalFunc;
import org.apache.pig.data.*;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.util.UDFContext;

import java.io.IOException;
import java.util.*;

/**
 * A Pig UDF to run a query against a Vespa cluster and return the
 * results.
 *
 * @author lesters
 */
public class VespaQuery extends EvalFunc<DataBag> {

    private final String PROPERTY_QUERY_TEMPLATE = "query";
    private final String PROPERTY_QUERY_SCHEMA = "schema";
    private final String PROPERTY_ROOT_NODE = "rootnode";

    private final VespaConfiguration configuration;
    private final Properties properties;
    private final String queryTemplate;
    private final String querySchema;
    private final String queryRootNode;

    private VespaHttpClient httpClient;

    public VespaQuery() {
        this(new String[0]);
    }

    public VespaQuery(String... params) {
        configuration = VespaConfiguration.get(UDFContext.getUDFContext().getJobConf(), null);
        properties = VespaConfiguration.loadProperties(params);

        queryTemplate = properties.getProperty(PROPERTY_QUERY_TEMPLATE);
        if (queryTemplate == null || queryTemplate.isEmpty()) {
            throw new IllegalArgumentException("Query template cannot be empty");
        }

        querySchema = properties.getProperty(PROPERTY_QUERY_SCHEMA, "rank:int,id:chararray");
        queryRootNode = properties.getProperty(PROPERTY_ROOT_NODE, "root/children");
    }

    @Override
    public DataBag exec(Tuple input) throws IOException {
        if (input == null || input.size() == 0) {
            return null;
        }
        JsonNode jsonResult = queryVespa(input);
        if (jsonResult == null) {
            return null;
        }
        return createPigRepresentation(jsonResult);
    }

    @Override
    public Schema outputSchema(Schema input) {
        return VespaQuerySchema.getPigSchema(querySchema);
    }


    private JsonNode queryVespa(Tuple input) throws IOException {
        String url = createVespaQueryUrl(input);
        if (url == null) {
            return null;
        }
        String result = executeVespaQuery(url);
        return parseVespaResultJson(result);
    }


    private String createVespaQueryUrl(Tuple input) throws IOException {
        return TupleTools.toString(getInputSchema(), input, queryTemplate);
    }


    private String executeVespaQuery(String url) throws IOException {
        if (httpClient == null) {
            httpClient = new VespaHttpClient(configuration);
        }
        return httpClient.get(url);
    }

    private JsonNode parseVespaResultJson(String result) throws IOException {
        return httpClient == null ? null : httpClient.parseResultJson(result, queryRootNode);
    }

    private DataBag createPigRepresentation(JsonNode hits) {
        DataBag bag = new SortedDataBag(null);
        VespaQuerySchema querySchema = new VespaQuerySchema(this.querySchema);

        for (int rank = 0; rank < hits.size(); ++rank) {
            JsonNode hit = hits.get(rank);
            Tuple tuple = querySchema.buildTuple(rank, hit);
            bag.add(tuple);
        }

        return bag;
    }




}
