// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/config/config-stor-prioritymapping.h>
#include <vespa/config/config.h>
#include <vespa/documentapi/messagebus/priority.h>
#include <atomic>
#include <array>

namespace storage {

class PriorityConverter
        : public config::IFetcherCallback<
                vespa::config::content::core::StorPrioritymappingConfig>
{
public:
    typedef vespa::config::content::core::StorPrioritymappingConfig Config;

    PriorityConverter(const config::ConfigUri& configUri);

    /** Converts the given priority into a storage api priority number. */
    uint8_t toStoragePriority(documentapi::Priority::Value) const;

    /** Converts the given priority into a document api priority number. */
    documentapi::Priority::Value toDocumentPriority(uint8_t) const;

    void configure(std::unique_ptr<Config> config) override;

private:
    static_assert(documentapi::Priority::PRI_ENUM_SIZE == 16,
                  "Unexpected size of priority enumeration");
    static_assert(documentapi::Priority::PRI_LOWEST == 15,
                  "Priority enum value out of bounds");
    static constexpr size_t PRI_ENUM_SIZE = documentapi::Priority::PRI_ENUM_SIZE;

    std::array<std::atomic<uint8_t>, PRI_ENUM_SIZE> _mapping;
    std::map<uint8_t, documentapi::Priority::Value> _reverseMapping;
    vespalib::Lock _mutex;

    config::ConfigFetcher _configFetcher;
};

} // storage
