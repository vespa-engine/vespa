// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace eval {

class Function;
enum class PassParams;

/**
 * Function used to generate a binary key that may be used to query
 * the compilation cache.
 **/
vespalib::string gen_key(const Function &function, PassParams pass_params);

} // namespace vespalib::eval
} // namespace vespalib

