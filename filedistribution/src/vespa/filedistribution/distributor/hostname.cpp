// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hostname.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/net/socket_address.h>
#include <vespa/log/log.h>
LOG_SETUP(".hostname");

namespace filedistribution {

std::string
lookupIPAddress(const std::string& hostName)
{
    auto best_addr = vespalib::SocketAddress::select_remote(0, hostName.c_str());
    if (!best_addr.valid()) {
        throw filedistribution::FailedResolvingHostName(hostName, VESPA_STRLOC);
    }
    const std::string address = best_addr.ip_address();
    LOG(debug, "Resolved hostname'%s' as '%s'", hostName.c_str(), address.c_str());
    return address;
}

VESPA_IMPLEMENT_EXCEPTION(FailedResolvingHostName, vespalib::Exception);

}
