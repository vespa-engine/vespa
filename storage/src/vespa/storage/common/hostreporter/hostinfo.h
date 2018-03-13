// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/jsonstream.h>

#include "versionreporter.h"

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

private:
    std::vector<HostReporter*> customReporters;
    VersionReporter versionReporter;
};

}

