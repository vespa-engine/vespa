// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/**
 * Interface for running maintenance jobs (cf. IMaintenanceJob).
 */
class IMaintenanceJobRunner
{
public:
    /*
     * Schedule job to be run in the future.
     */
    virtual void run() = 0;
    virtual ~IMaintenanceJobRunner() = default;
};

} // namespace proton

