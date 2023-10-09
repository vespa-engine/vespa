// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace storage::distributor {

class MaintenancePriority
{
public:
    enum Priority {
        NO_MAINTENANCE_NEEDED,
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH,
        HIGHEST,
        PRIORITY_LIMIT
    };

    static constexpr const char* toString(Priority pri) noexcept {
        switch (pri) {
        case NO_MAINTENANCE_NEEDED: return "NO_MAINTENANCE_NEEDED";
        case VERY_LOW:              return "VERY_LOW";
        case LOW:                   return "LOW";
        case MEDIUM:                return "MEDIUM";
        case HIGH:                  return "HIGH";
        case VERY_HIGH:             return "VERY_HIGH";
        case HIGHEST:               return "HIGHEST";
        default:                    return "INVALID";
        }
    }

    constexpr MaintenancePriority() noexcept
        : _priority(NO_MAINTENANCE_NEEDED)
    {}

    constexpr explicit MaintenancePriority(Priority priority) noexcept
        : _priority(priority)
    {}

    constexpr Priority getPriority() const noexcept {
        return _priority;
    }

    constexpr bool requiresMaintenance() const noexcept {
        return _priority != NO_MAINTENANCE_NEEDED;
    }

    static constexpr MaintenancePriority noMaintenanceNeeded() noexcept {
        return MaintenancePriority(NO_MAINTENANCE_NEEDED);
    }

private:
    Priority _priority;
};

}
