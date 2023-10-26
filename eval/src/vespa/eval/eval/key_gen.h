// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib::eval {

class Function;
enum class PassParams : uint8_t;

/**
 * Function used to generate a binary key that may be used to query
 * the compilation cache.
 **/
vespalib::string gen_key(const Function &function, PassParams pass_params);

}
