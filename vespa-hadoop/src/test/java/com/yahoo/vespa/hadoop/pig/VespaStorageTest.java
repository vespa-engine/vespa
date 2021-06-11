// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.pig;

import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaCounters;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.mapred.Counters;
import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.backend.executionengine.ExecJob;
import org.apache.pig.tools.pigstats.JobStats;
import org.apache.pig.tools.pigstats.PigStats;
import org.apache.pig.tools.pigstats.mapreduce.MRJobStats;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VespaStorageTest {

    @Test
    public void requireThatPremadeXmlOperationsFeedSucceeds() throws Exception {
        Configuration conf = new HdfsConfiguration();
        conf.set(VespaConfiguration.DATA_FORMAT, "xml");
        assertAllDocumentsOk("src/test/pig/feed_operations_xml.pig", conf);
    }


    @Test
    public void requireThatPremadeOperationsFeedSucceeds() throws Exception {
        assertAllDocumentsOk("src/test/pig/feed_operations.pig");
    }


    @Test
    public void requireThatPremadeMultilineOperationsFeedSucceeds() throws Exception {
        assertAllDocumentsOk("src/test/pig/feed_multiline_operations.pig");
    }


    @Test
    public void requireThatPremadeOperationsWithJsonLoaderFeedSucceeds() throws Exception {
        assertAllDocumentsOk("src/test/pig/feed_operations_with_json_loader.pig");
    }

    @Test
    public void requireThatPremadeOperationsWithJsonLoaderFeedAndNonLegacyClientSucceeds() throws Exception {
        Configuration conf = new HdfsConfiguration();
        conf.set(VespaConfiguration.USE_SSL, Boolean.TRUE.toString());
        conf.set(VespaConfiguration.USE_LEGACY_CLIENT, Boolean.FALSE.toString());
        assertAllDocumentsOk("src/test/pig/feed_operations_with_json_loader.pig", conf);
    }

    @Test
    public void requireThatCreateOperationsFeedSucceeds() throws Exception {
        assertAllDocumentsOk("src/test/pig/feed_create_operations.pig");
    }


    @Test
    public void requireThatCreateOperationsShortFormFeedSucceeds() throws Exception {
        assertAllDocumentsOk("src/test/pig/feed_create_operations_short_form.pig");
    }


    @Test
    public void requireThatFeedVisitDataSucceeds() throws Exception {
        assertAllDocumentsOk("src/test/pig/feed_visit_data.pig");
    }


    private PigServer setup(String script, Configuration conf) throws Exception {
        if (conf == null) {
            conf = new HdfsConfiguration();
        }
        conf.setIfUnset(VespaConfiguration.DRYRUN, "true");
        conf.setIfUnset(VespaConfiguration.ENDPOINT, "dummy-endpoint");

        // Parameter substitutions - can also be set by configuration
        Map<String, String> parameters = new HashMap<>();
        parameters.put("ENDPOINT", "endpoint-does-not-matter-in-dryrun,another-endpoint-that-does-not-matter");

        PigServer ps = new PigServer(ExecType.LOCAL, conf);
        ps.setBatchOn();
        ps.registerScript(script, parameters);

        return ps;
    }


    private void assertAllDocumentsOk(String script) throws Exception {
        assertAllDocumentsOk(script, null);
    }


    private void assertAllDocumentsOk(String script, Configuration conf) throws Exception {
        PigServer ps = setup(script, conf);
        List<ExecJob> jobs = ps.executeBatch();
        PigStats stats = jobs.get(0).getStatistics();
        for (JobStats js : stats.getJobGraph()) {
            Counters hadoopCounters = ((MRJobStats)js).getHadoopCounters();
            assertNotNull(hadoopCounters);
            VespaCounters counters = VespaCounters.get(hadoopCounters);
            assertEquals(10, counters.getDocumentsSent());
            assertEquals(0, counters.getDocumentsFailed());
            assertEquals(10, counters.getDocumentsOk());
        }
    }

}
