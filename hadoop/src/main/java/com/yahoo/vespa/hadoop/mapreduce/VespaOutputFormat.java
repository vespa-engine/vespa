package com.yahoo.vespa.hadoop.mapreduce;

import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaCounters;
import org.apache.hadoop.mapreduce.*;

import java.io.IOException;
import java.util.Properties;

/**
 * An output specification for writing to Vespa instances in a Map-Reduce job.
 * Mainly returns an instance of a {@link VespaRecordWriter} that does the
 * actual feeding to Vespa.
 *
 * @author lesters
 */
@SuppressWarnings("rawtypes")
public class VespaOutputFormat extends OutputFormat {

    private final Properties configOverride;

    public VespaOutputFormat() {
        super();
        this.configOverride = null;
    }

    public VespaOutputFormat(Properties configOverride) {
        super();
        this.configOverride = configOverride;
    }


    @Override
    public RecordWriter getRecordWriter(TaskAttemptContext context) throws IOException, InterruptedException {
        VespaCounters counters = VespaCounters.get(context);
        VespaConfiguration configuration = VespaConfiguration.get(context.getConfiguration(), configOverride);
        return new VespaRecordWriter(configuration, counters, context);
    }


    @Override
    public OutputCommitter getOutputCommitter(TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
        return new VespaOutputCommitter();
    }


    @Override
    public void checkOutputSpecs(JobContext jobContext) throws IOException, InterruptedException {
        System.out.println("Hei");
    }

}
