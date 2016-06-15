// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vespa/filedistribution/common/exception.h>

namespace filedistribution {

namespace errorinfo {
typedef boost::error_info<struct tag_HostName, std::string> HostName;
typedef boost::error_info<struct tag_Port, int> Port;
};


std::string lookupIPAddress(const std::string& hostName);

struct FailedResolvingHostName : public Exception {
    FailedResolvingHostName(const std::string& hostName) {
        *this <<errorinfo::HostName(hostName);
    }
};
}
