// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace config {

static const uint64_t DEFAULT_NEXTCONFIG_TIMEOUT = 55000;
static const uint64_t DEFAULT_SUBSCRIBE_TIMEOUT = 55000;
static const uint64_t DEFAULT_GETCONFIGS_TIMEOUT = 55000;


struct TimingValues
{
    uint64_t successTimeout;        // Timeout when previous config request was a success.
    uint64_t errorTimeout;          // Timeout when previous config request was an error.
    uint64_t initialTimeout;        // Timeout used when requesting config for the first time.
    uint64_t subscribeTimeout;    // Timeout used to find out when to give up unsubscribe.

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
                 uint64_t initSubscribeTimeout,
                 uint64_t initFixedDelay,
                 uint64_t initSuccessDelay,
                 uint64_t initUnconfiguredDelay,
                 uint64_t initConfiguredErrorDelay,
                 unsigned int initMaxDelayMultiplier,
                 uint64_t initTransientDelay,
                 uint64_t initFatalDelay);
};

}

