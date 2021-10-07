// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.pig;

import com.yahoo.vespa.hadoop.mapreduce.VespaSimpleJsonInputFormat;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.pig.LoadFunc;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.PigSplit;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;

import java.io.IOException;

/**
 * Simple JSON loader which loads either one JSON object per line or a
 * multiline JSON consisting of objects in an array.
 *
 * Returns only the textual representation of the JSON object.
 *
 * @author lesters
 */
@SuppressWarnings("rawtypes")
public class VespaSimpleJsonLoader extends LoadFunc {

    private TupleFactory tupleFactory = TupleFactory.getInstance();
    private VespaSimpleJsonInputFormat.VespaJsonRecordReader recordReader;

    @Override
    public void setLocation(String location, Job job) throws IOException {
        FileInputFormat.setInputPaths(job, location);
    }

    @Override
    public InputFormat getInputFormat() throws IOException {
        return new VespaSimpleJsonInputFormat();
    }

    @Override
    public void prepareToRead(RecordReader reader, PigSplit split) throws IOException {
        recordReader = (VespaSimpleJsonInputFormat.VespaJsonRecordReader) reader;
    }

    @Override
    public Tuple getNext() throws IOException {
        try {
            boolean done = recordReader.nextKeyValue();
            if (done) {
                return null;
            }
            Text json = recordReader.getCurrentKey();
            if (json == null) {
                return null;
            }
            return tupleFactory.newTuple(json.toString());

        } catch (InterruptedException ex) {
            return null;
        }
    }
}
