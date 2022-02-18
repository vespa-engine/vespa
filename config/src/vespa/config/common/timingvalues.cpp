// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "timingvalues.h"

namespace config {


const vespalib::duration DEFAULT_NEXTCONFIG_TIMEOUT(55s);
const vespalib::duration DEFAULT_SUBSCRIBE_TIMEOUT(55s);
const vespalib::duration DEFAULT_GETCONFIGS_TIMEOUT(55s);

TimingValues::TimingValues()
    : successTimeout(600s),
      errorTimeout(25s),
      initialTimeout(15s),
      subscribeTimeout(DEFAULT_SUBSCRIBE_TIMEOUT),
      fixedDelay(5s),
      successDelay(250ms),
      unconfiguredDelay(1s),
      configuredErrorDelay(15s),
      maxDelayMultiplier(10),
      transientDelay(60s),
      fatalDelay(60s)
{ }


TimingValues::TimingValues(duration initSuccessTimeout,
                           duration initErrorTimeout,
                           duration initInitialTimeout,
                           duration initSubscribeTimeout,
                           duration initFixedDelay,
                           duration initSuccessDelay,
                           duration initUnconfiguredDelay,
                           duration initConfiguredErrorDelay,
                           unsigned int initMaxDelayMultiplier,
                           duration initTransientDelay,
                           duration initFatalDelay)
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
