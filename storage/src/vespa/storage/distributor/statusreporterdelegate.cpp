// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statusreporterdelegate.h"

namespace storage::distributor {

StatusReporterDelegate::StatusReporterDelegate(
        framework::ComponentRegister& compReg,
        const StatusDelegator& delegator,
        const framework::StatusReporter& target)
    : framework::StatusReporter(target.getId(), target.getName()),
      _delegator(delegator),
      _target(target),
      _component(compReg, std::string(target.getId()) + "_status")
{
}

StatusReporterDelegate::~StatusReporterDelegate() = default;

vespalib::string
StatusReporterDelegate::getReportContentType(const framework::HttpUrlPath& path) const
{
    // Implementation must be data race free.
    return _target.getReportContentType(path);
}

bool
StatusReporterDelegate::reportStatus(std::ostream& out,
                                     const framework::HttpUrlPath& path) const
{
    return _delegator.handleStatusRequest(
            DelegatedStatusRequest(_target, path, out));
}

void
StatusReporterDelegate::registerStatusPage()
{
    _component.registerStatusPage(*this);
}

}

