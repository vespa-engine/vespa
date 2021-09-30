// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "error.h"
#include "errorcode.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

namespace mbus {

Error::Error()
    : _code(ErrorCode::NONE),
      _msg(),
      _service()
{ }

Error::~Error() = default;

Error::Error(uint32_t c, vespalib::stringref m, vespalib::stringref s)
    : _code(c),
      _msg(m),
      _service(s)
{ }

string
Error::toString() const
{
    string name(ErrorCode::getName(_code));
    if (name.empty()) {
        name = fmt("%u", _code);
    }
    return fmt("[%s @ %s]: %s", name.c_str(), _service.empty() ? "localhost" : _service.c_str(), _msg.c_str());
}

} // namespace mbus
