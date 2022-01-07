// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.topicpredictor;

import java.util.logging.Logger;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;

import com.yahoo.fsa.FSA;
import com.yahoo.fsa.MetaData;


/**
 * Class for accessing the topic prediction automata. Look up the
 * predicted topics for a term. Each topic has an attached weight and
 * a term vector (topicSegments).
 *
 * @author Peter Boros
 */
public class TopicPredictor extends MetaData {

    private static final String packageName = "com.yahoo.fsa.topicpredictor";

    private final FSA fsa;

    public TopicPredictor(String fsafile, String datfile){
        this(fsafile, datfile, "utf-8");
    }

    public TopicPredictor(String fsafile, String datfile,
                          String charsetname) {
        super(datfile, charsetname);
        if (!isOk()) {
            Logger.getLogger(packageName).
                warning("Error initializing predictor with file " + datfile);
        }

        // Init the segment->'topic index' FSA
        fsa = new FSA(fsafile);
        if (!fsa.isOk()) {
            Logger.getLogger(packageName).
                warning("Error initializing FSA with file " + fsafile);
        }
    }

    /**
     * Returns a list of PredictedTopic objects, one for each topic
     * the segment maps to. The returned list contains all topics,
     * as opposed to the two-argument version.
     * @param segment   The segment string to find (all) topics for.
     * @return (Linked)List of PredictedTopic objects.  */
    public List<PredictedTopic> getPredictedTopics(String segment) {
        return getPredictedTopics(segment, 0);
    }

    /**
     * Returns a list of PredictedTopic objects, one for each topic
     * the segment maps to. The returned list length is cut off at
     * 'maxTopics' entries, maxTopics=0 returns all topics.
     * @param segment   The segment string to find topics for.
     * @param maxTopics The max number of topics to return, 0 for all topics
     * @return (Linked)List of PredictedTopic objects.  */
    public List<PredictedTopic> getPredictedTopics(String segment, int maxTopics) {
        List<PredictedTopic> predictedTopics = new LinkedList<>();

        int segIdx = getSegmentIndex(segment);
        int[][] topicArr = getTopicArray(segIdx, maxTopics);
        int numTopics = topicArr.length;
        int allTopics = getNumTopics(segIdx);
        /*Logger.getLogger(packageName).
            fine("Segment: '" + segment + "' has " + allTopics +
                 " topics in automaton, fetched " + numTopics);
        */
        for(int i=0; i < numTopics; i++) {
            int weight = topicArr[i][1];
            String[] topicInfo= getTopicInfo(topicArr[i][0]);
            String topic = topicInfo[0];
            String vector= topicInfo[1];
            PredictedTopic pt =
                new PredictedTopic(topic, (double)weight, vector);
            predictedTopics.add(pt);
        }

        return predictedTopics;
    }

    /**
     * Returns the index (hash value) of the input segment in the FSA.
     * @param segment   The segment string to find index for.
     * @return Index for this segment in the FSA. */
    private int getSegmentIndex(String segment) {
        FSA.State s = fsa.getState();
        s.delta(segment);
        if (s.isFinal()) {
            return s.hash();
        }
        return -1;
    }

    /**
     * Returns the number of topics the FSA contains for the input
     * segment.
     * @return Number of topics for the segment. */
    private int getNumTopics(int segIdx) {
        if (segIdx < 0) {
            return 0;
        }
        ByteBuffer buf =  getIndirectRecordEntry(segIdx, 4);
        return buf.getInt(0);
    }

    /**
     * Reads the topics and other metadata for a segment from the
     * (memory-mapped) metadata file. Returns the info in a
     * two-dimensional array (one row per topic).
     * @param segIdx     The FSA index (hash value) for the segment.
     * @param maxTopics  Max number of topics to return, 0 for all topics.
     * @return Number of topics for the segment. */
    private int[][] getTopicArray(int segIdx, int maxTopics) {
        if (segIdx < 0) {
            return new int[0][0];
        }

        int numTopics = getNumTopics(segIdx);
        if ((maxTopics > 0) && (numTopics > maxTopics)) {
            numTopics = maxTopics;
        }

        int[][] topics = new int[numTopics][2];
        ByteBuffer buf =  getIndirectRecordEntry(segIdx,4+8*numTopics);
        for(int i=0; i<numTopics; i++){
            topics[i][0] = buf.getInt(4+8*i);
            topics[i][1] = buf.getInt(8+8*i);
        }
        return topics;
    }

    /**
     * Returns the topic and vector strings from the internal meta
     * data structure.
     * @param topicId Topic start index in a two-dimensional array
     * @return topic string at [0] and vector string at [1] */
    private String[] getTopicInfo(int topicId) {
        return getStringArrayEntry(user(0) + topicId, 2);
    }

}
