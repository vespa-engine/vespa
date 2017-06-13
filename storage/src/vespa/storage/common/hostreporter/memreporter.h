// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#ifndef STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_MEMREPORTER_H_
#define STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_MEMREPORTER_H_

#include "hostreporter.h"

namespace storage {

class MemReporter: public HostReporter {
public:
    MemReporter();
    ~MemReporter() override;

    void report(vespalib::JsonStream& jsonreport) override;
};

} /* namespace storage */

#endif /* STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_MEMREPORTER_H_ */
