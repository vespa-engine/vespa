// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::VisitorThreadMetrics
 * @ingroup visiting
 *
 * @brief Metrics for the visitor threads.
 *
 * @version $Id$
 */
#pragma once

#include <vespa/metrics/metrics.h>

namespace storage {

struct VisitorThreadMetrics : public metrics::MetricSet
{
    typedef metrics::DoubleAverageMetric DOUBLE;
    typedef metrics::LongAverageMetric COUNT;

    metrics::LongAverageMetric queueSize;
    metrics::LoadMetric<DOUBLE> averageQueueWaitingTime;
    metrics::LoadMetric<DOUBLE> averageVisitorLifeTime;
    metrics::LoadMetric<DOUBLE> averageVisitorCreationTime;
    metrics::LoadMetric<DOUBLE> averageMessageSendTime;
    metrics::LoadMetric<DOUBLE> averageProcessingTime;
    metrics::LoadMetric<COUNT> createdVisitors;
    metrics::LoadMetric<COUNT> abortedVisitors;
    metrics::LoadMetric<COUNT> completedVisitors;
    metrics::LoadMetric<COUNT> failedVisitors;
    metrics::LoadMetric<COUNT> visitorDestinationFailureReplies;

    VisitorThreadMetrics(const std::string& name,
                         const std::string& desc,
                         const metrics::LoadTypeSet& loadTypes)
        : metrics::MetricSet(name, "visitor partofsum thread", desc),
          queueSize("queuesize", "",
                  "Size of input message queue.", this),
          averageQueueWaitingTime(
                  loadTypes,
                  DOUBLE("averagequeuewait",
                         "",
                         "Average time an operation spends in input queue."),
                  this),
          averageVisitorLifeTime(
                  loadTypes,
                  DOUBLE("averagevisitorlifetime",
                         "",
                         "Average lifetime of a visitor"),
                  this),
          averageVisitorCreationTime(
                  loadTypes,
                  DOUBLE("averagevisitorcreationtime",
                         "",
                         "Average time spent creating a visitor instance"),
                  this),
          averageMessageSendTime(
                  loadTypes,
                  DOUBLE("averagemessagesendtime",
                         "",
                         "Average time it takes for messages to be sent to "
                         "their target (and be replied to)"),
                  this),
          averageProcessingTime(
                  loadTypes,
                  DOUBLE("averageprocessingtime",
                         "",
                         "Average time visitor uses in handleDocuments() call"),
                  this),
          createdVisitors(
                  loadTypes,
                  COUNT("created",
                        "",
                        "Number of visitors created."),
                  this),
          abortedVisitors(
                  loadTypes,
                  COUNT("aborted",
                        "",
                        "Number of visitors aborted."),
                  this),
          completedVisitors(
                  loadTypes,
                  COUNT("completed",
                        "",
                        "Number of visitors completed"),
                  this),
          failedVisitors(
                  loadTypes,
                  COUNT("failed",
                        "",
                        "Number of visitors failed"),
                  this),
          visitorDestinationFailureReplies(
                loadTypes,
                COUNT("destination_failure_replies",
                      "",
                      "Number of failure replies received from "
                      "the visitor destination"),
                this)
    {
        queueSize.unsetOnZeroValue();
    }

};

}

