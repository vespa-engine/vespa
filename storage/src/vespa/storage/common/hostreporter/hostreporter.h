// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/jsonstream.h>

namespace storage {
/**
 * Interface for reporters. Each implementation should add a json entry, e.g. for
 * cpu it should be named "cpu".
 */
class HostReporter {
public:
	virtual void report(vespalib::JsonStream& jsonreport) = 0;
	virtual ~HostReporter() = default;
};
}

