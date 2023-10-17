// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/documentapi/messagebus/priority.h>
#include <array>
#include <vector>

namespace storage {

class PriorityConverter {
public:
    PriorityConverter();
    ~PriorityConverter();

    /** Converts the given priority into a storage api priority number. */
    [[nodiscard]] uint8_t toStoragePriority(documentapi::Priority::Value) const noexcept;

    /** Converts the given priority into a document api priority number. */
    [[nodiscard]] documentapi::Priority::Value toDocumentPriority(uint8_t storage_priority) const noexcept {
        return _reverse_mapping[storage_priority];
    }

private:
    void init_static_priority_mappings();

    static_assert(documentapi::Priority::PRI_ENUM_SIZE == 16, "Unexpected size of priority enumeration");
    static_assert(documentapi::Priority::PRI_LOWEST == 15, "Priority enum value out of bounds");
    static constexpr size_t PRI_ENUM_SIZE = documentapi::Priority::PRI_ENUM_SIZE;

    std::array<uint8_t, PRI_ENUM_SIZE>        _mapping;
    std::vector<documentapi::Priority::Value> _reverse_mapping;
};

} // storage
