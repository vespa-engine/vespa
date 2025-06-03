// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/jsonstream.h>
#include <chrono>

namespace storage {
/**
 * Interface for reporters. Each implementation should add a json entry, e.g. for
 * cpu it should be named "cpu".
 */
class HostReporter {
public:
	virtual ~HostReporter() = default;

	virtual void report(vespalib::JsonStream& jsonreport) = 0;
	virtual void on_periodic_callback(std::chrono::steady_clock::time_point) { /*no-op by default*/ }
};
}

