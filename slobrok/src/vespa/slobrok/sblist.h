// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cfg.h"
#include <mutex>

namespace slobrok::api {

/**
 * @brief List of connection specs for service location brokers
 **/
class SlobrokList : public Configurable {
public:
    /**
     * @brief Create a new SlobrokList object, initially empty
     **/
    SlobrokList();

    /**
     * setup with a list of connection specs;
     * should be called at least once.
     * @param specList should not be an empty list.
     **/
    void setup(const std::vector<std::string> &specList) override;

    /**
     * retrieve the spec for next slobrok server to try.
     * NOTE: when the list is exhausted the empty string will
     * be returned once before looping and retrying.
     **/
    std::string nextSlobrokSpec();

    /** obtain how many times we have tried all possible servers */
    size_t retryCount() const { return _retryCount; }

    /** check if setup has been called successfully */
    bool ok() const { return _slobrokSpecs.size() > 0; }

    /** return a string (for logging) with all specs in the list */
    std::string logString();

    /** check if the list contains a given spec */
    bool contains(const std::string &spec);
private:
    std::mutex _lock;
    std::vector<std::string> _slobrokSpecs;
    size_t _nextSpec;
    size_t _currSpec;
    size_t _retryCount;
};

}
