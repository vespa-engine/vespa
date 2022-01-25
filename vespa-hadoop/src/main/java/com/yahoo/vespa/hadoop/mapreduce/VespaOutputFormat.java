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
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

import static com.yahoo.vespa.http.client.config.FeedParams.DataFormat.XML_UTF8;

/**
 * An output specification for writing to Vespa instances in a Map-Reduce job.
 * Mainly returns an instance of a {@link LegacyVespaRecordWriter} that does the
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
        Boolean useLegacyClient = configuration.useLegacyClient().orElse(null);
        if (Objects.equals(useLegacyClient, Boolean.TRUE) || configuration.dataFormat() == XML_UTF8) {
            log.warning("Feeding with legacy client or XML will no longer be supported on Vespa 8. " +
                    "See https://docs.vespa.ai/en/vespa8-release-notes.html");
            return new LegacyVespaRecordWriter(configuration, counters);
        } else {
            return new VespaRecordWriter(configuration, counters);
        }
    }


    @Override
    public OutputCommitter getOutputCommitter(TaskAttemptContext taskAttemptContext) throws IOException, InterruptedException {
        return new VespaOutputCommitter();
    }


    @Override
    public void checkOutputSpecs(JobContext jobContext) throws IOException, InterruptedException {
    }

}
