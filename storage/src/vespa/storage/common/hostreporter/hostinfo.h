// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "versionreporter.h"
#include <vespa/vespalib/util/jsonstream.h>

namespace storage {

/**
 * Reports status about this host. It has a set of default reporters and additional
 * reporters might be added.
 */
class HostInfo {
public:
    HostInfo();
    ~HostInfo();

    void printReport(vespalib::JsonStream& report);
    // Does not take ownership.
    void registerReporter(HostReporter* reporter);

    void invoke_periodic_callbacks(std::chrono::steady_clock::time_point now_steady);

private:
    std::vector<HostReporter*> customReporters;
    VersionReporter versionReporter;
};

}

