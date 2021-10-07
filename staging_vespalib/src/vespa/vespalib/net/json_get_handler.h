// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <map>

namespace vespalib {

struct JsonGetHandler {
    virtual vespalib::string get(const vespalib::string &host,
                                 const vespalib::string &path,
                                 const std::map<vespalib::string,vespalib::string> &params) const = 0;
    virtual ~JsonGetHandler() {}
};

} // namespace vespalib
