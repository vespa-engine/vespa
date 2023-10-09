// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#ifndef STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_VERSIONREPORTER_H_
#define STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_VERSIONREPORTER_H_

#include "hostreporter.h"

namespace storage {

// Reports Vtag.
class VersionReporter: public HostReporter {
public:
    VersionReporter() {}
    ~VersionReporter() override {}

    void report(vespalib::JsonStream& jsonreport) override;
};

} /* namespace storage */

#endif /* STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_VERSIONREPORTER_H_ */
