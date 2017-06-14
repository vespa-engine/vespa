// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#ifndef STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_HOSTINFO_H_
#define STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_HOSTINFO_H_

#include <vespa/vespalib/util/jsonstream.h>

#include "cpureporter.h"
#include "diskreporter.h"
#include "memreporter.h"
#include "networkreporter.h"
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
    CpuReporter cpuReporter;
    DiskReporter diskReporter;
    MemReporter memReporter;
    NetworkReporter networkReporter;
    VersionReporter versionReporter;
};

} /* namespace storage */

#endif /* STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_HOSTINFO_H_ */
