// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.mapreduce;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.BufferedInputStream;
import java.io.IOException;

/**
 * Simple JSON reader which splits the input file along JSON object boundaries.
 *
 * There are two cases handled here:
 *  1. Each line contains a JSON object, i.e. { ... }
 *  2. The file contains an array of objects with arbitrary line breaks, i.e. [ {...}, {...} ]
 *
 *  Not suitable for cases where you want to extract objects from some other arbitrary structure.
 *
 *  TODO: Support config which points to a array in the JSON as start point for object extraction,
 *        ala how it is done in VespaHttpClient.parseResultJson, i.e. support rootNode config.
 *
 * @author lesters
 */
public class VespaSimpleJsonInputFormat extends FileInputFormat<Text, NullWritable> {

    @Override
    public RecordReader<Text, NullWritable> createRecordReader(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
        return new VespaJsonRecordReader();
    }

    public static class VespaJsonRecordReader extends RecordReader<Text, NullWritable> {
        private long remaining;
        private JsonParser parser;
        private Text currentKey;
        private NullWritable currentValue = NullWritable.get();

        @Override
        public void initialize(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
            FileSplit fileSplit = (FileSplit) split;
            FSDataInputStream stream = FileSystem.get(context.getConfiguration()).open(fileSplit.getPath());
            if (fileSplit.getStart() != 0) {
                stream.seek(fileSplit.getStart());
            }

            remaining = fileSplit.getLength();
            JsonFactory factory = new JsonFactoryBuilder().disable(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES).build();
            parser = factory.createParser(new BufferedInputStream(stream));
            parser.setCodec(new ObjectMapper());
            parser.nextToken();
            if (parser.currentToken() == JsonToken.START_ARRAY) {
                parser.nextToken();
            }
        }

        @Override
        public boolean nextKeyValue() throws IOException, InterruptedException {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                return true;
            }
            currentKey = new Text(parser.readValueAsTree().toString());
            parser.nextToken();
            return false;
        }

        @Override
        public Text getCurrentKey() throws IOException, InterruptedException {
            return currentKey;
        }

        @Override
        public NullWritable getCurrentValue() throws IOException, InterruptedException {
            return currentValue;
        }

        @Override
        public float getProgress() throws IOException, InterruptedException {
            return parser.getCurrentLocation().getByteOffset() / remaining;
        }

        @Override
        public void close() throws IOException {
            parser.close();
        }
    }

}

