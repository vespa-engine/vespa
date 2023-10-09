// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fsa.topicpredictor;


/**
 * Class encapsulation of a predicted topic. A topic has a weight and
 * a term vector string of topicSegments.
 *
 * @author  gjoranv
 **/
public class PredictedTopic {

    private String       topic  = "";
    private double       weight = 0.0;
    private String       vector = "";


    public PredictedTopic(String topic, double weight, String vector){
        this.topic = topic;
        this.weight = weight;
        this.vector = vector;
    }

    public PredictedTopic(String topic, double weight){
        this(topic, weight, "");
    }


    /** Returns the topic */
    public String getTopic() { return topic; }

    /** Returns the weight */
    public double getWeight() { return weight; }

    /** Returns the vector*/
    public String getVector() { return vector; }


    /** Sets the weight */
    public void setWeight(double weight) {
        this.weight = weight;
    }

    /** Adds to the weight */
    public void addWeight(double weight) {
        this.weight += weight;
    }

    /** Sets the vector*/
    public void setVector(String vector) {
        this.vector = vector;
    }

    /** Compares this topic to another topic, according to weight descending */
    public int compareDescendWeight(Object o) {
        PredictedTopic pt = (PredictedTopic)o;

        double wgt1 = getWeight();
        double wgt2 = pt.getWeight();
        if (wgt1 < wgt2) { return 1; }
        if (wgt1 > wgt2) { return -1;}
        return 0;
    }

}
