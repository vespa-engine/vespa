// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "config_validator_result_type.h"
#include <vespa/vespalib/stllike/string.h>

namespace proton {
namespace configvalidator {

/*
 * The result of a schema check, with message string for more detailed info.
 */
class Result
{
private:
    ResultType _type;
    vespalib::string _what;
public:
    Result()
        : _type(ResultType::OK),
          _what("")
    {}
    Result(ResultType type_, const vespalib::string &what_)
        : _type(type_),
          _what(what_)
    {}
    ResultType type() const { return _type; }
    const vespalib::string &what() const { return _what; }
    bool ok() const { return type() == ResultType::OK; }
};

} // namespace proton::configvalidator
} // namespace proton
