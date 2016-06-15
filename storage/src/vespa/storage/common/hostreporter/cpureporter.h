// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#ifndef STORAGE_SRC_CPP_STORAGE_COMMON_HOSTINFO_CPUREPORTER_H_
#define STORAGE_SRC_CPP_STORAGE_COMMON_HOSTINFO_CPUREPORTER_H_

#include "hostreporter.h"

namespace storage {

class CpuReporter: public HostReporter {
public:
    void report(vespalib::JsonStream& jsonreport) override;

	CpuReporter() {}
	~CpuReporter() override {}
};

} /* namespace storage */

#endif /* STORAGE_SRC_CPP_STORAGE_COMMON_HOSTINFO_CPUREPORTER_H_ */
