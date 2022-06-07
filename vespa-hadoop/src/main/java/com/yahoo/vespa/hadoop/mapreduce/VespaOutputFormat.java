// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.mapreduce;

import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaCounters;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * An output specification for writing to Vespa instances in a Map-Reduce job.
 * Mainly returns an instance of a {@link VespaRecordWriter} that does the
 * actual feeding to Vespa.
 *
 * @author lesters
 */
@SuppressWarnings("rawtypes")
public class VespaOutputFormat extends OutputFormat {

    private static final Logger log = Logger.getLogger(VespaOutputFormat.class.getName());

    final Properties configOverride;

    public VespaOutputFormat() {
        super();
        this.configOverride = null;
    }

    public VespaOutputFormat(Properties configOverride) {
        super();
        this.configOverride = configOverride;
    }


    @Override
    @SuppressWarnings("deprecation")
    public RecordWriter getRecordWriter(TaskAttemptContext context) throws IOException {
        VespaCounters counters = VespaCounters.get(context);
        VespaConfiguration configuration = VespaConfiguration.get(context.getConfiguration(), configOverride);
        return new VespaRecordWriter(configuration, counters);
    }


    @Override
    public OutputCommitter getOutputCommitter(TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
        return new VespaOutputCommitter();
    }


    @Override
    public void checkOutputSpecs(JobContext jobContext) throws IOException, InterruptedException {
    }

}
