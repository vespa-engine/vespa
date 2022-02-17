// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/config/config-stor-prioritymapping.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/documentapi/messagebus/priority.h>
#include <atomic>
#include <array>
#include <mutex>

namespace config {
    class ConfigUri;
    class ConfigFetcher;
}

namespace storage {

class PriorityConverter
        : public config::IFetcherCallback<
                vespa::config::content::core::StorPrioritymappingConfig>
{
public:
    typedef vespa::config::content::core::StorPrioritymappingConfig Config;

    explicit PriorityConverter(const config::ConfigUri& configUri);
    ~PriorityConverter() override;

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
    mutable std::mutex _mutex;

    std::unique_ptr<config::ConfigFetcher> _configFetcher;
};

} // storage
