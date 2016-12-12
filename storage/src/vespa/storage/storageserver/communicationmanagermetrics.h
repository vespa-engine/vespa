// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class CommunicationManagerMetrics
 * \ingroup storageserver
 *
 * \brief Metrics for the communication manager.
 */

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {

struct CommunicationManagerMetrics : public metrics::MetricSet {
    metrics::LongAverageMetric queueSize;
    metrics::LoadMetric<metrics::DoubleAverageMetric> messageProcessTime;
    metrics::LoadMetric<metrics::DoubleAverageMetric> exceptionMessageProcessTime;
    metrics::LongCountMetric failedDueToTooLittleMemory;
    metrics::LongCountMetric convertToStorageAPIFailures;
    metrics::DoubleAverageMetric sendCommandLatency;
    metrics::DoubleAverageMetric sendReplyLatency;

    CommunicationManagerMetrics(const metrics::LoadTypeSet& loadTypes,
                                metrics::MetricSet* owner = 0)
        : metrics::MetricSet("communication", "",
                "Metrics for the communication manager", owner),
          queueSize("messagequeue", "", "Size of input message queue.", this),
          messageProcessTime(loadTypes, metrics::DoubleAverageMetric(
                  "messageprocesstime", "",
                  "Time transport thread uses to process a single message"),
                  this),
          exceptionMessageProcessTime(loadTypes, metrics::DoubleAverageMetric(
                  "exceptionmessageprocesstime", "",
                  "Time transport thread uses to process a single message "
                  "that fails with an exception thrown into communication "
                  "manager"),
                  this),
          failedDueToTooLittleMemory("toolittlememory", "",
                  "Number of messages failed due to too little memory "
                  "available", this),
          convertToStorageAPIFailures("convertfailures", "",
                  "Number of messages that failed to get converted to "
                  "storage API messages", this),
          sendCommandLatency("sendcommandlatency", "",
                  "Average ms used to send commands to MBUS", this),
          sendReplyLatency("sendreplylatency", "",
                  "Average ms used to send replies to MBUS", this)
    {
    }

};

}

