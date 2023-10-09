// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace config::sentinel {

enum class CcResult { UNKNOWN, CONN_FAIL, UNREACHABLE_UP, INDIRECT_PING_FAIL, INDIRECT_PING_UNAVAIL, ALL_OK };

}
