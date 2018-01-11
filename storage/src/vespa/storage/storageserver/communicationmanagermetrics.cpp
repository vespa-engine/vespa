// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "communicationmanagermetrics.h"
#include <vespa/documentapi/loadtypes/loadtypeset.h>

using namespace metrics;
namespace storage {

CommunicationManagerMetrics::CommunicationManagerMetrics(const LoadTypeSet& loadTypes, MetricSet* owner)
    : MetricSet("communication", "", "Metrics for the communication manager", owner),
      queueSize("messagequeue", "", "Size of input message queue.", this),
      messageProcessTime(loadTypes,
                         DoubleAverageMetric("messageprocesstime", "",
                                             "Time transport thread uses to process a single message"),
                         this),
      exceptionMessageProcessTime(loadTypes,
                                  DoubleAverageMetric("exceptionmessageprocesstime", "",
                                                      "Time transport thread uses to process a single message "
                                                      "that fails with an exception thrown into communication manager"),
                                  this),
      failedDueToTooLittleMemory("toolittlememory", "", "Number of messages failed due to too little memory available", this),
      convertToStorageAPIFailures("convertfailures", "",
                                  "Number of messages that failed to get converted to storage API messages", this),
      bucketSpaceMappingFailures("bucket_space_mapping_failures", "",
                                 "Number of messages that could not be resolved to a known bucket space", this),
      sendCommandLatency("sendcommandlatency", "", "Average ms used to send commands to MBUS", this),
      sendReplyLatency("sendreplylatency", "", "Average ms used to send replies to MBUS", this)
{
}

CommunicationManagerMetrics::~CommunicationManagerMetrics() = default;

}

