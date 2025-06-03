// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hostreporter.h"

namespace storage {

// Reports Vtag.
class VersionReporter: public HostReporter {
public:
    VersionReporter() = default;
    ~VersionReporter() override = default;

    void report(vespalib::JsonStream& jsonreport) override;
};

} /* namespace storage */
