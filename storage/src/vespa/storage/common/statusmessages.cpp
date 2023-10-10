// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statusmessages.h"
#include <ostream>

namespace storage {

RequestStatusPage::RequestStatusPage(const framework::HttpUrlPath& path)
    : api::InternalCommand(ID),
      _path(path),
      _sortToken()
{ }

RequestStatusPage::~RequestStatusPage() { }

void
RequestStatusPage::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << "RequestStatusPage()";

    if (verbose) {
        out << " : ";
        InternalCommand::print(out, true, indent);
    }
}

RequestStatusPageReply::RequestStatusPageReply(const RequestStatusPage& cmd, const std::string& status)
    : api::InternalReply(ID, cmd),
      _status(status),
      _sortToken(cmd.getSortToken())
{ }

RequestStatusPageReply::~RequestStatusPageReply() { }

void
RequestStatusPageReply::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << "RequestStatusPageReply()";

    if (verbose) {
        out << " : ";
        InternalReply::print(out, true, indent);
    }
}

std::unique_ptr<api::StorageReply>
RequestStatusPage::makeReply()
{
    return std::make_unique<RequestStatusPageReply>(*this, "");
}

}
