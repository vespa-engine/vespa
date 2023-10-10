// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#ifndef VESPA_STORAGE_COMMON_UTIL
#define VESPA_STORAGE_COMMON_UTIL

#include <vespa/storage/common/hostreporter/hostreporter.h>
#include <vespa/vespalib/data/slime/slime.h>

namespace storage {
namespace util {

void
reporterToSlime(HostReporter &hostReporter, vespalib::Slime &slime);
}
}

#endif // VESPA_STORAGE_COMMON_UTIL
