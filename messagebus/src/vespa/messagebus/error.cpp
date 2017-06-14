// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "error.h"
#include "errorcode.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace mbus {

Error::Error()
    : _code(ErrorCode::NONE),
      _msg(),
      _service()
{ }

Error::~Error() {}

Error::Error(uint32_t c, const string &m, const string &s)
    : _code(c),
      _msg(m),
      _service(s)
{ }

string
Error::toString() const
{
    string name(ErrorCode::getName(_code));
    if (name.empty()) {
        name = vespalib::make_vespa_string("%u", _code);
    }
    return vespalib::make_vespa_string("[%s @ %s]: %s", name.c_str(),
                                 _service.empty() ? "localhost" : _service.c_str(),
                                 _msg.c_str());
}

} // namespace mbus
