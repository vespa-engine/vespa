// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hostinfo.h"
#include "hostreporter.h"

namespace storage {

HostInfo::HostInfo() {
    registerReporter(&versionReporter);
}

HostInfo::~HostInfo() = default;

void HostInfo::printReport(vespalib::JsonStream& report) {
    for (HostReporter* reporter : customReporters) {
        reporter->report(report);
    }
}

void HostInfo::registerReporter(HostReporter *reporter) {
    customReporters.emplace_back(reporter);
}

void HostInfo::invoke_periodic_callbacks(std::chrono::steady_clock::time_point now_steady) {
    for (HostReporter* reporter : customReporters) {
        reporter->on_periodic_callback(now_steady);
    }
}

}
