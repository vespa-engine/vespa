// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::StatusReporterMap
 * \ingroup status
 *
 * \brief Interface to access the various status reporters
 */
#pragma once

#include <vector>

namespace storage::framework {

struct StatusReporter;

struct StatusReporterMap {
    virtual ~StatusReporterMap() = default;

    virtual const StatusReporter* getStatusReporter(vespalib::stringref id) = 0;

    virtual std::vector<const StatusReporter*> getStatusReporters() = 0;
};

} // storage::framework
