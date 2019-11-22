// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <chrono>

namespace config {

extern const std::chrono::milliseconds DEFAULT_NEXTCONFIG_TIMEOUT;
extern const std::chrono::milliseconds DEFAULT_SUBSCRIBE_TIMEOUT;
extern const std::chrono::milliseconds DEFAULT_GETCONFIGS_TIMEOUT;


struct TimingValues
{
    using milliseconds = std::chrono::milliseconds;
    uint64_t successTimeout;        // Timeout when previous config request was a success.
    uint64_t errorTimeout;          // Timeout when previous config request was an error.
    uint64_t initialTimeout;        // Timeout used when requesting config for the first time.
    milliseconds subscribeTimeout;    // Timeout used to find out when to give up unsubscribe.

    uint64_t fixedDelay;            // Fixed delay between config requests.
    uint64_t successDelay;          // Delay if until next request after successful getConfig.
    uint64_t unconfiguredDelay;     // Delay if failed and client not yet configured.
    uint64_t configuredErrorDelay;  // Delay if failed but client has gotten config for the first time earlier.
    unsigned int maxDelayMultiplier;        // Max multiplier when trying to get config.

    uint64_t transientDelay;        // Delay between connection reuse if transient error.
    uint64_t fatalDelay;            // Delay between connection reuse if fatal error.

    TimingValues();
    TimingValues(uint64_t initSuccessTimeout,
                 uint64_t initerrorTimeout,
                 uint64_t initInitialTimeout,
                 milliseconds initSubscribeTimeout,
                 uint64_t initFixedDelay,
                 uint64_t initSuccessDelay,
                 uint64_t initUnconfiguredDelay,
                 uint64_t initConfiguredErrorDelay,
                 unsigned int initMaxDelayMultiplier,
                 uint64_t initTransientDelay,
                 uint64_t initFatalDelay);
};

}

