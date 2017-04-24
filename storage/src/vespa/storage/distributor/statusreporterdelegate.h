// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageframework/storageframework.h>
#include <vespa/storage/distributor/delegatedstatusrequest.h>
#include <vespa/storage/distributor/statusdelegator.h>

namespace storage {
namespace distributor {

class StatusReporterDelegate
    : public framework::StatusReporter
{
    const StatusDelegator& _delegator;
    const framework::StatusReporter& _target;
    framework::Component _component;
public:
    StatusReporterDelegate(framework::ComponentRegister& compReg,
                           const StatusDelegator& delegator,
                           const framework::StatusReporter& target);

    void registerStatusPage();

    vespalib::string getReportContentType(
            const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream&, const framework::HttpUrlPath&) const override;
};

} // distributor
} // storage
