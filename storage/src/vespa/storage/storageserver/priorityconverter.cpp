// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "priorityconverter.h"
#include <map>

namespace storage {

PriorityConverter::PriorityConverter()
    : _mapping(),
      _reverse_mapping()
{
    init_static_priority_mappings();
}

PriorityConverter::~PriorityConverter() = default;

void
PriorityConverter::init_static_priority_mappings()
{
    // Defaults from `stor-prioritymapping` config
    constexpr uint8_t highest   = 50;
    constexpr uint8_t very_high = 60;
    constexpr uint8_t high_1    = 70;
    constexpr uint8_t high_2    = 80;
    constexpr uint8_t high_3    = 90;
    constexpr uint8_t normal_1  = 100;
    constexpr uint8_t normal_2  = 110;
    constexpr uint8_t normal_3  = 120;
    constexpr uint8_t normal_4  = 130;
    constexpr uint8_t normal_5  = 140;
    constexpr uint8_t normal_6  = 150;
    constexpr uint8_t low_1     = 160;
    constexpr uint8_t low_2     = 170;
    constexpr uint8_t low_3     = 180;
    constexpr uint8_t very_low  = 190;
    constexpr uint8_t lowest    = 200;

    _mapping[documentapi::Priority::PRI_HIGHEST]   = highest;
    _mapping[documentapi::Priority::PRI_VERY_HIGH] = very_high;
    _mapping[documentapi::Priority::PRI_HIGH_1]    = high_1;
    _mapping[documentapi::Priority::PRI_HIGH_2]    = high_2;
    _mapping[documentapi::Priority::PRI_HIGH_3]    = high_3;
    _mapping[documentapi::Priority::PRI_NORMAL_1]  = normal_1;
    _mapping[documentapi::Priority::PRI_NORMAL_2]  = normal_2;
    _mapping[documentapi::Priority::PRI_NORMAL_3]  = normal_3;
    _mapping[documentapi::Priority::PRI_NORMAL_4]  = normal_4;
    _mapping[documentapi::Priority::PRI_NORMAL_5]  = normal_5;
    _mapping[documentapi::Priority::PRI_NORMAL_6]  = normal_6;
    _mapping[documentapi::Priority::PRI_LOW_1]     = low_1;
    _mapping[documentapi::Priority::PRI_LOW_2]     = low_2;
    _mapping[documentapi::Priority::PRI_LOW_3]     = low_3;
    _mapping[documentapi::Priority::PRI_VERY_LOW]  = very_low;
    _mapping[documentapi::Priority::PRI_LOWEST]    = lowest;

    std::map<uint8_t, documentapi::Priority::Value> reverse_map_helper;
    reverse_map_helper[highest]   = documentapi::Priority::PRI_HIGHEST;
    reverse_map_helper[very_high] = documentapi::Priority::PRI_VERY_HIGH;
    reverse_map_helper[high_1]    = documentapi::Priority::PRI_HIGH_1;
    reverse_map_helper[high_2]    = documentapi::Priority::PRI_HIGH_2;
    reverse_map_helper[high_3]    = documentapi::Priority::PRI_HIGH_3;
    reverse_map_helper[normal_1]  = documentapi::Priority::PRI_NORMAL_1;
    reverse_map_helper[normal_2]  = documentapi::Priority::PRI_NORMAL_2;
    reverse_map_helper[normal_3]  = documentapi::Priority::PRI_NORMAL_3;
    reverse_map_helper[normal_4]  = documentapi::Priority::PRI_NORMAL_4;
    reverse_map_helper[normal_5]  = documentapi::Priority::PRI_NORMAL_5;
    reverse_map_helper[normal_6]  = documentapi::Priority::PRI_NORMAL_6;
    reverse_map_helper[low_1]     = documentapi::Priority::PRI_LOW_1;
    reverse_map_helper[low_2]     = documentapi::Priority::PRI_LOW_2;
    reverse_map_helper[low_3]     = documentapi::Priority::PRI_LOW_3;
    reverse_map_helper[very_low]  = documentapi::Priority::PRI_VERY_LOW;
    reverse_map_helper[lowest]    = documentapi::Priority::PRI_LOWEST;

    // Precompute a 1-1 LUT to avoid having to lower-bound lookup values in a fixed map
    _reverse_mapping.resize(256);
    for (size_t i = 0; i < 256; ++i) {
        auto iter = reverse_map_helper.lower_bound(static_cast<uint8_t>(i));
        _reverse_mapping[i] = (iter != reverse_map_helper.cend()) ? iter->second : documentapi::Priority::PRI_LOWEST;
    }
}

uint8_t
PriorityConverter::toStoragePriority(documentapi::Priority::Value documentApiPriority) const noexcept
{
    const auto index = static_cast<uint32_t>(documentApiPriority);
    if (index >= PRI_ENUM_SIZE) {
        return 255;
    }
    return _mapping[index];
}

} // storage
