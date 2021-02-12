// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace storage {
namespace distributor {

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

    static const std::string toString(Priority pri) {
        switch (pri) {
        case NO_MAINTENANCE_NEEDED: return "NO_MAINTENANCE_NEEDED";
        case VERY_LOW: return "VERY_LOW";
        case LOW: return "LOW";
        case MEDIUM: return "MEDIUM";
        case HIGH: return "HIGH";
        case VERY_HIGH: return "VERY_HIGH";
        case HIGHEST: return "HIGHEST";
        default: return "INVALID";
        }
    }

    MaintenancePriority()
        : _priority(NO_MAINTENANCE_NEEDED)
    {}

    explicit MaintenancePriority(Priority priority)
        : _priority(priority)
    {}

    Priority getPriority() const {
        return _priority;
    }

    bool requiresMaintenance() const {
        return _priority != NO_MAINTENANCE_NEEDED;
    }

    static MaintenancePriority noMaintenanceNeeded() {
        return MaintenancePriority(NO_MAINTENANCE_NEEDED);
    }

private:
    Priority _priority;
};

}
}


