// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "priorityconverter.h"
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/helper/configfetcher.hpp>


namespace storage {

PriorityConverter::PriorityConverter(const config::ConfigUri & configUri)
    : _configFetcher(std::make_unique<config::ConfigFetcher>(configUri.getContext()))
{
    _configFetcher->subscribe<vespa::config::content::core::StorPrioritymappingConfig>(configUri.getConfigId(), this);
    _configFetcher->start();
}

PriorityConverter::~PriorityConverter() = default;

uint8_t
PriorityConverter::toStoragePriority(documentapi::Priority::Value documentApiPriority) const
{
    const uint32_t index(static_cast<uint32_t>(documentApiPriority));
    if (index >= PRI_ENUM_SIZE) {
        return 255;
    }

    return _mapping[index];
}

documentapi::Priority::Value
PriorityConverter::toDocumentPriority(uint8_t storagePriority) const
{
    std::lock_guard guard(_mutex);
    std::map<uint8_t, documentapi::Priority::Value>::const_iterator iter =
        _reverseMapping.lower_bound(storagePriority);

    if (iter != _reverseMapping.end()) {
        return iter->second;
    }

    return documentapi::Priority::PRI_LOWEST;
}

void
PriorityConverter::configure(std::unique_ptr<vespa::config::content::core::StorPrioritymappingConfig> config)
{
    // Data race free; _mapping is an array of std::atomic.
    _mapping[documentapi::Priority::PRI_HIGHEST] = config->highest;
    _mapping[documentapi::Priority::PRI_VERY_HIGH] = config->veryHigh;
    _mapping[documentapi::Priority::PRI_HIGH_1] = config->high1;
    _mapping[documentapi::Priority::PRI_HIGH_2] = config->high2;
    _mapping[documentapi::Priority::PRI_HIGH_3] = config->high3;
    _mapping[documentapi::Priority::PRI_NORMAL_1] = config->normal1;
    _mapping[documentapi::Priority::PRI_NORMAL_2] = config->normal2;
    _mapping[documentapi::Priority::PRI_NORMAL_3] = config->normal3;
    _mapping[documentapi::Priority::PRI_NORMAL_4] = config->normal4;
    _mapping[documentapi::Priority::PRI_NORMAL_5] = config->normal5;
    _mapping[documentapi::Priority::PRI_NORMAL_6] = config->normal6;
    _mapping[documentapi::Priority::PRI_LOW_1] = config->low1;
    _mapping[documentapi::Priority::PRI_LOW_2] = config->low2;
    _mapping[documentapi::Priority::PRI_LOW_3] = config->low3;
    _mapping[documentapi::Priority::PRI_VERY_LOW] = config->veryLow;
    _mapping[documentapi::Priority::PRI_LOWEST] = config->lowest;

    std::lock_guard guard(_mutex);
    _reverseMapping.clear();
    _reverseMapping[config->highest] = documentapi::Priority::PRI_HIGHEST;
    _reverseMapping[config->veryHigh] = documentapi::Priority::PRI_VERY_HIGH;
    _reverseMapping[config->high1] = documentapi::Priority::PRI_HIGH_1;
    _reverseMapping[config->high2] = documentapi::Priority::PRI_HIGH_2;
    _reverseMapping[config->high3] = documentapi::Priority::PRI_HIGH_3;
    _reverseMapping[config->normal1] = documentapi::Priority::PRI_NORMAL_1;
    _reverseMapping[config->normal2] = documentapi::Priority::PRI_NORMAL_2;
    _reverseMapping[config->normal3] = documentapi::Priority::PRI_NORMAL_3;
    _reverseMapping[config->normal4] = documentapi::Priority::PRI_NORMAL_4;
    _reverseMapping[config->normal5] = documentapi::Priority::PRI_NORMAL_5;
    _reverseMapping[config->normal6] = documentapi::Priority::PRI_NORMAL_6;
    _reverseMapping[config->low1] = documentapi::Priority::PRI_LOW_1;
    _reverseMapping[config->low2] = documentapi::Priority::PRI_LOW_2;
    _reverseMapping[config->low3] = documentapi::Priority::PRI_LOW_3;
    _reverseMapping[config->veryLow] = documentapi::Priority::PRI_VERY_LOW;
    _reverseMapping[config->lowest] = documentapi::Priority::PRI_LOWEST;
}

} // storage
