// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/retriever/configsnapshot.h>
#include <vespa/vespalib/stllike/string.h>

namespace searchcorespi {

/**
 * Class that keeps the config used when constructing an index manager.
 */
class IndexManagerConfig {
private:
    vespalib::string _configId;
    const config::ConfigSnapshot &_configSnapshot;
    size_t _numSearcherThreads;

public:
    IndexManagerConfig(const vespalib::string &configId,
                       const config::ConfigSnapshot &configSnapshot,
                       size_t numSearcherThreads);
    ~IndexManagerConfig();

    /**
     * Returns the config id used to retrieve the configs from the config snapshot instance.
     */
    const vespalib::string &getConfigId() const { return _configId; }

    /**
     * Returns the snapshot containing configs to be used by the index manager.
     */
    const config::ConfigSnapshot &getConfigSnapshot() const { return _configSnapshot; }

    /**
     * Returns the number of searcher threads that are used to query the index manager.
     */
    size_t getNumSearcherThreads() const { return _numSearcherThreads; }
};

} // namespace searchcorespi


