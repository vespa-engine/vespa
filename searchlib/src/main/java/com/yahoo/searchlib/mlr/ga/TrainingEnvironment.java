// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

/**
 * The static environment of a training session
 *
 * @author bratseth
 */
public class TrainingEnvironment {

    // TODO: Not sure if this belongs ... or should even be an instance
    // TODO: maybe collapse Trainer into this and call it TrainingSession
    private final Recombiner recombiner;
    private final Tracker tracker;
    private final TrainingSet trainingSet;
    private final TrainingParameters parameters;

    public TrainingEnvironment(Recombiner recombiner, Tracker tracker,
                               TrainingSet trainingSet, TrainingParameters parameters) {
        this.recombiner = recombiner;
        this.tracker = tracker;
        this.trainingSet = trainingSet;
        this.parameters = parameters;
    }

    public Recombiner recombiner() { return recombiner; }
    public Tracker tracker() { return tracker; }
    public TrainingSet trainingSet() { return trainingSet; }
    public TrainingParameters parameters() { return parameters; }

}
