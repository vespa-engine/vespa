// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/maintenance/maintenanceoperation.h>
#include <vespa/storage/distributor/maintenance/maintenancepriority.h>

namespace storage::distributor {

class MaintenancePriorityAndType
{
    MaintenancePriority _priority;
    MaintenanceOperation::Type _type;
public:
    constexpr MaintenancePriorityAndType(MaintenancePriority pri,
                                         MaintenanceOperation::Type type) noexcept
        : _priority(pri),
          _type(type)
    {}

    constexpr MaintenancePriority getPriority() const noexcept {
        return _priority;
    }

    constexpr MaintenanceOperation::Type getType() const noexcept {
        return _type;
    }

    constexpr bool requiresMaintenance() const noexcept {
        return (_priority.getPriority()
                != MaintenancePriority::NO_MAINTENANCE_NEEDED);
    }
};

} // storage::distributor
