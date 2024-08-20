// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace vespalib {

/**
 * Simple utility used to determine which host we are running on. The
 * get function should return the fully qualified host name.
 **/
class HostName
{
private:
    static const std::string _host_name;
public:
    static const std::string &get() { return _host_name; }
};

} // namespace vespalib
