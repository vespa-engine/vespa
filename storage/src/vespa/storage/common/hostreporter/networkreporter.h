// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#ifndef STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_NETWORKREPORTER_H_
#define STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_NETWORKREPORTER_H_

#include "hostreporter.h"

namespace storage {

class NetworkReporter: public HostReporter {
public:
    NetworkReporter() {};
    ~NetworkReporter() override {};

    void report(vespalib::JsonStream& jsonreport) override;
};

} /* namespace storage */

#endif /* STORAGE_SRC_CPP_STORAGE_COMMON_HOSTREPORTER_NETWORKREPORTER_H_ */
