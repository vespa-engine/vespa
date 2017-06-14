// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vespa/filedistribution/common/exception.h>

namespace filedistribution {

std::string lookupIPAddress(const std::string& hostName);

VESPA_DEFINE_EXCEPTION(FailedResolvingHostName, vespalib::Exception);

}
