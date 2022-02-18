// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/time.h>

namespace config {

extern const vespalib::duration DEFAULT_NEXTCONFIG_TIMEOUT;
extern const vespalib::duration DEFAULT_SUBSCRIBE_TIMEOUT;
extern const vespalib::duration DEFAULT_GETCONFIGS_TIMEOUT;


struct TimingValues
{
    using duration = vespalib::duration;
    duration successTimeout;        // Timeout when previous config request was a success.
    duration errorTimeout;          // Timeout when previous config request was an error.
    duration initialTimeout;        // Timeout used when requesting config for the first time.
    duration subscribeTimeout;    // Timeout used to find out when to give up unsubscribe.

    duration fixedDelay;            // Fixed delay between config requests.
    duration successDelay;          // Delay if until next request after successful getConfig.
    duration unconfiguredDelay;     // Delay if failed and client not yet configured.
    duration configuredErrorDelay;  // Delay if failed but client has gotten config for the first time earlier.
    unsigned int maxDelayMultiplier;        // Max multiplier when trying to get config.

    duration transientDelay;        // Delay between connection reuse if transient error.
    duration fatalDelay;            // Delay between connection reuse if fatal error.

    TimingValues();
    TimingValues(duration initSuccessTimeout,
                 duration initerrorTimeout,
                 duration initInitialTimeout,
                 duration initSubscribeTimeout,
                 duration initFixedDelay,
                 duration initSuccessDelay,
                 duration initUnconfiguredDelay,
                 duration initConfiguredErrorDelay,
                 unsigned int initMaxDelayMultiplier,
                 duration initTransientDelay,
                 duration initFatalDelay);
};

}

