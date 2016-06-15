// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/exceptions.h>

namespace storage {
namespace spi {

/**
 * Exception used where the cause has already been reported to the user, so
 * one only wants to wind back to caller, and not have it log it or print
 * backtrace.
 *
 * Used to create good log errors, and avoid caller printing backtrace, or an
 * inspecific error.
 */
VESPA_DEFINE_EXCEPTION(HandledException, vespalib::Exception);

} // spi
} // storage

