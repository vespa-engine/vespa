// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "timingvalues.h"

namespace config {

using std::chrono::milliseconds;

const milliseconds DEFAULT_NEXTCONFIG_TIMEOUT(55000);
const milliseconds DEFAULT_SUBSCRIBE_TIMEOUT(55000);
const milliseconds DEFAULT_GETCONFIGS_TIMEOUT(55000);

TimingValues::TimingValues()
    : successTimeout(600000),
      errorTimeout(25000),
      initialTimeout(15000),
      subscribeTimeout(DEFAULT_SUBSCRIBE_TIMEOUT),
      fixedDelay(5000),
      successDelay(250),
      unconfiguredDelay(1000),
      configuredErrorDelay(15000),
      maxDelayMultiplier(10),
      transientDelay(10000),
      fatalDelay(60000)
{ }


TimingValues::TimingValues(uint64_t initSuccessTimeout,
                           uint64_t initErrorTimeout,
                           uint64_t initInitialTimeout,
                           milliseconds initSubscribeTimeout,
                           uint64_t initFixedDelay,
                           uint64_t initSuccessDelay,
                           uint64_t initUnconfiguredDelay,
                           uint64_t initConfiguredErrorDelay,
                           unsigned int initMaxDelayMultiplier,
                           uint64_t initTransientDelay,
                           uint64_t initFatalDelay)
    : successTimeout(initSuccessTimeout),
      errorTimeout(initErrorTimeout),
      initialTimeout(initInitialTimeout),
      subscribeTimeout(initSubscribeTimeout),
      fixedDelay(initFixedDelay),
      successDelay(initSuccessDelay),
      unconfiguredDelay(initUnconfiguredDelay),
      configuredErrorDelay(initConfiguredErrorDelay),
      maxDelayMultiplier(initMaxDelayMultiplier),
      transientDelay(initTransientDelay),
      fatalDelay(initFatalDelay)
{ }

}
