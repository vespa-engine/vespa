// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::ApplicationGenerationFetcher
 * \ingroup storageserver
 *
 * \brief Interface for fetching application generation number and
 * component name.
 */

#pragma once

#include <cstdint>
#include <string>

namespace storage {

class ApplicationGenerationFetcher {
public:
    virtual ~ApplicationGenerationFetcher() {}

    virtual int64_t getGeneration() const = 0;
    virtual std::string getComponentName() const = 0;
};

} // storage

