// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "delegatedstatusrequest.h"
#include "statusdelegator.h"
#include <vespa/storageframework/generic/component/component.h>

namespace storage::distributor {

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
    ~StatusReporterDelegate() override;

    void registerStatusPage();
    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream&, const framework::HttpUrlPath&) const override;
};

} // storage::distributor
