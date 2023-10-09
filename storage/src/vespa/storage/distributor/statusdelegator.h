// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace storage::distributor {

struct DelegatedStatusRequest;

class StatusDelegator
{
public:
    virtual ~StatusDelegator() = default;

    virtual bool handleStatusRequest(const DelegatedStatusRequest& request) const = 0;
};

} // storage::distributor
