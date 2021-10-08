// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.mapreduce;

import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;

/**
 * The output committer describes the commit task output for a Map-Reduce
 * job. Not currently used, but is part of the Hadoop protocol since 2.7.
 *
 * @author lesters
 */
public class VespaOutputCommitter extends OutputCommitter {
    @Override
    public void setupJob(JobContext jobContext) throws IOException {
    }

    @Override
    public void setupTask(TaskAttemptContext taskAttemptContext) throws IOException {
    }

    @Override
    public boolean needsTaskCommit(TaskAttemptContext taskAttemptContext) throws IOException {
        return false;
    }

    @Override
    public void commitTask(TaskAttemptContext taskAttemptContext) throws IOException {
    }

    @Override
    public void abortTask(TaskAttemptContext taskAttemptContext) throws IOException {
    }
}
