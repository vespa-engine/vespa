// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \file forcelink.h
 *
 * \brief Utility to link in objects we need in binary.
 */

#pragma once

#include <vespa/documentapi/documentapi.h>
#include <vespa/config-rank-profiles.h>

namespace storage {

    extern void serverForceLink();

}

