// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "hostname.h"

#include <boost/asio.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".hostname");
#include <vespa/vespalib/net/socket_address.h>

namespace asio = boost::asio;

std::string
filedistribution::lookupIPAddress(const std::string& hostName)
{
    auto best_addr = vespalib::SocketAddress::select_remote(0, hostName.c_str());
    if (!best_addr.valid()) {
        BOOST_THROW_EXCEPTION(filedistribution::FailedResolvingHostName(hostName));
    }
    const std::string address = best_addr.ip_address();
    LOG(debug, "Resolved hostname'%s' as '%s'", hostName.c_str(), address.c_str());
    return address;
}
