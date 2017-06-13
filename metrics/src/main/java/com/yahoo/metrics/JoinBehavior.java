// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

/**
 * When joining multiple metrics as a result of dimension
 * removal. Should the result be an average or a sum?  As an example,
 * a latency metric should likely be averaged, while a number of
 * pending metric should likely be summed. This join behavior property
 * lets the metric framework know how to remove dimensions.
 **/
public enum JoinBehavior { AVERAGE_ON_JOIN, SUM_ON_JOIN }
