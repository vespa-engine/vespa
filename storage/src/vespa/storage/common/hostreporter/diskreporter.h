// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#ifndef STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_DISKREPORTER_H_
#define STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_DISKREPORTER_H_

#include "hostreporter.h"

namespace storage {

class DiskReporter: public HostReporter {
public:
    DiskReporter();
    ~DiskReporter() override;

    void report(vespalib::JsonStream& jsonreport) override;
};

} /* namespace storage */

#endif /* STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_DISKREPORTER_H_ */
