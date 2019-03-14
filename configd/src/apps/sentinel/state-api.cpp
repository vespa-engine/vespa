// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "state-api.h"
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace {

std::map<vespalib::string, vespalib::string> noParams;

} // namespace <unnamed>


namespace config {
namespace sentinel {

vespalib::string
StateApi::get(const char *path) const
{
    return myStateApi.get(host_and_port, path, noParams);
}

void
StateApi::bound(int port)
{
    host_and_port = vespalib::make_string("%s:%d", vespalib::HostName::get().c_str(), port);
}

} // namespace config::sentinel
} // namespace config
