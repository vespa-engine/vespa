// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "identity.h"
#include <vespa/vespalib/util/host_name.h>

namespace mbus {

Identity::Identity(const string &configId) :
    _hostname(),
    _servicePrefix(configId)
{
    _hostname = vespalib::HostName::get();
}

Identity::~Identity() = default;

std::vector<string>
Identity::split(const string &name)
{
    std::vector<string> ret;
    string::size_type pos = 0;
    string::size_type split = name.find_first_of('/');
    while (split != string::npos) {
        ret.push_back(string(name, pos, split - pos));
        pos = split + 1;
        split = name.find_first_of('/', pos);
    }
    ret.push_back(string(name, pos));
    return ret;
}

} // namespace mbus
