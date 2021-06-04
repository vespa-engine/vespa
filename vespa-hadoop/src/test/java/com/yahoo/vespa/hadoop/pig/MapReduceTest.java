// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.pig;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.vespa.hadoop.mapreduce.VespaOutputFormat;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaCounters;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.test.PathUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapReduceTest {

    protected static File hdfsBaseDir;
    protected static FileSystem hdfs;
    protected static Configuration conf;
    protected static MiniDFSCluster cluster;

    protected static Path metricsJsonPath;
    protected static Path metricsCsvPath;

    @BeforeAll
    public static void setUp() throws IOException {
        hdfsBaseDir = new File(PathUtils.getTestDir(MapReduceTest.class).getCanonicalPath());

        conf = new HdfsConfiguration();
        conf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, hdfsBaseDir.getAbsolutePath());
        conf.set(VespaConfiguration.DRYRUN, "true");
        conf.set(VespaConfiguration.ENDPOINT, "endpoint-does-not-matter-in-dryrun");

        cluster = new MiniDFSCluster.Builder(conf).build();
        hdfs = FileSystem.get(conf);

        metricsJsonPath = new Path("metrics_json");
        metricsCsvPath = new Path("metrics_csv");
        copyToHdfs("src/test/resources/operations_data.json", metricsJsonPath, "data");
        copyToHdfs("src/test/resources/tabular_data.csv", metricsCsvPath, "data");
    }

    @AfterAll
    public static void tearDown() throws IOException {
        Path testDir = new Path(hdfsBaseDir.getParent());
        hdfs.delete(testDir, true);
        cluster.shutdown();
        LocalFileSystem localFileSystem = FileSystem.getLocal(conf);
        localFileSystem.delete(testDir, true);
    }

    @Test
    public void requireThatMapOnlyJobSucceeds() throws Exception {
        Job job = Job.getInstance(conf);
        job.setJarByClass(MapReduceTest.class);
        job.setMapperClass(FeedMapper.class);
        job.setOutputFormatClass(VespaOutputFormat.class);
        job.setMapOutputValueClass(Text.class);

        FileInputFormat.setInputPaths(job, metricsJsonPath);

        boolean success = job.waitForCompletion(true);
        assertTrue(success, "Job Failed");

        VespaCounters counters = VespaCounters.get(job);
        assertEquals(10, counters.getDocumentsSent());
        assertEquals(0, counters.getDocumentsFailed());
        assertEquals(10, counters.getDocumentsOk());
    }

    @Test
    public void requireThatMapReduceJobSucceeds() throws Exception {
        Job job = Job.getInstance(conf);
        job.setJarByClass(MapReduceTest.class);
        job.setMapperClass(FeedMapper.class);
        job.setOutputFormatClass(VespaOutputFormat.class);
        job.setMapOutputValueClass(Text.class);
        job.setReducerClass(FeedReducer.class);
        job.setNumReduceTasks(1);

        FileInputFormat.setInputPaths(job, metricsJsonPath);

        boolean success = job.waitForCompletion(true);
        assertTrue(success, "Job Failed");

        VespaCounters counters = VespaCounters.get(job);
        assertEquals(10, counters.getDocumentsSent());
        assertEquals(0, counters.getDocumentsFailed());
        assertEquals(10, counters.getDocumentsOk());
    }


    @Test
    public void requireThatTransformMapJobSucceeds() throws Exception {
        Job job = Job.getInstance(conf);
        job.setJarByClass(MapReduceTest.class);
        job.setMapperClass(ParsingMapper.class);
        job.setOutputFormatClass(VespaOutputFormat.class);
        job.setMapOutputValueClass(Text.class);
        job.setReducerClass(FeedReducer.class);
        job.setNumReduceTasks(1);

        FileInputFormat.setInputPaths(job, metricsCsvPath);

        boolean success = job.waitForCompletion(true);
        assertTrue(success, "Job Failed");

        VespaCounters counters = VespaCounters.get(job);
        assertEquals(10, counters.getDocumentsSent());
        assertEquals(0, counters.getDocumentsFailed());
        assertEquals(10, counters.getDocumentsOk());
        assertEquals(0, counters.getDocumentsSkipped());
    }


    private static void copyToHdfs(String localFile, Path hdfsDir, String hdfsName) throws IOException {
        Path hdfsPath = new Path(hdfsDir, hdfsName);
        FSDataOutputStream out = hdfs.create(hdfsPath);

        try (InputStream in = new BufferedInputStream(new FileInputStream(localFile))) {
            int len;
            byte[] buffer = new byte[1024];
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } finally {
            out.close();
        }
    }

    public static class FeedMapper extends Mapper<LongWritable, Text, LongWritable, Text> {
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            context.write(key, value);
        }
    }

    public static class FeedReducer extends Reducer<Object, Text, LongWritable, Text> {
        public void reduce(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            context.write(key, value);
        }
    }

    public static class ParsingMapper extends Mapper<LongWritable, Text, LongWritable, Text> {
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString();
            if (line == null || line.length() == 0)
                return;

            StringTokenizer tokenizer = new StringTokenizer(line);
            long date = Long.parseLong(tokenizer.nextToken());
            String metricName = tokenizer.nextToken();
            long metricValue = Long.parseLong(tokenizer.nextToken());
            String application = tokenizer.nextToken();

            String docid = "id:"+application+":metric::"+metricName+"-"+date;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JsonGenerator g = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);

            g.writeStartObject();
            g.writeObjectFieldStart("fields");
            g.writeNumberField("date", date);
            g.writeStringField("name", metricName);
            g.writeNumberField("value", metricValue);
            g.writeStringField("application", application);
            g.writeEndObject();
            g.writeStringField("put", docid);
            g.writeEndObject();
            g.close();

            context.write(key, new Text(out.toString()));
        }
    }


}
