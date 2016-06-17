// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import java.util.List;

/**
 * A producer of a list of cases for function training.
 *
 * @author bratseth
 */
public interface CaseList {

    public List<TrainingSet.Case> cases();

}
