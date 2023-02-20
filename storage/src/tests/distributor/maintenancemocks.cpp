// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenancemocks.h"

namespace storage::distributor {

MockOperationStarter::MockOperationStarter() noexcept
    : _shouldStart(true)
{}

MockOperationStarter::~MockOperationStarter() = default;

}
