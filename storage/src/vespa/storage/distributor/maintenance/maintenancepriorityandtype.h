// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/maintenance/maintenanceoperation.h>
#include <vespa/storage/distributor/maintenance/maintenancepriority.h>

namespace storage::distributor {

class MaintenancePriorityAndType
{
    MaintenancePriority _priority;
    MaintenanceOperation::Type _type;
public:
    MaintenancePriorityAndType(MaintenancePriority pri,
                               MaintenanceOperation::Type type)
        : _priority(pri),
          _type(type)
    {}

    const MaintenancePriority& getPriority() const {
        return _priority;
    }

    MaintenanceOperation::Type getType() const {
        return _type;
    }

    bool requiresMaintenance() const {
        return (_priority.getPriority()
                != MaintenancePriority::NO_MAINTENANCE_NEEDED);
    }
};

} // storage::distributor
