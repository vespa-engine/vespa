// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "countvisitor.h"
#include <vespa/persistence/spi/docentry.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".visitor.instance.countvisitor");

namespace storage {

CountVisitor::CountVisitor(StorageComponent& component,
                           const vdslib::Parameters& params)
    : Visitor(component),
      _doScheme(params.get("scheme") == "true"),
      _doNamespace(params.get("namespace") == "true"),
      _doUser(params.get("user") == "true"),
      _doGroup(params.get("group") == "true")
{
}

void
CountVisitor::handleDocuments(const document::BucketId& /*bucketId*/,
                              DocEntryList& entries,
                              HitCounter& hitCounter)
{
    for (const auto & entry : entries) {
        if (!entry->isRemove()) {
            const document::Document* doc = entry->getDocument();

            if (doc) {
                const document::IdString& idString = doc->getId().getScheme();
                hitCounter.addHit(doc->getId(), 0);

                if (_doNamespace) {
                    _namespaceCount[vespalib::string(idString.getNamespace())]++;
                }

                if (_doUser && idString.hasNumber()) {
                    _userCount[idString.getNumber()]++;
                }

                if (_doGroup && idString.hasGroup()) {
                    _groupCount[vespalib::string(idString.getGroup())]++;
                }

                if (_doScheme) {
                    _schemeCount["id"]++;
                }

            }
        }
    }
}

void CountVisitor::completedVisiting(HitCounter&) {
    auto cmd = std::make_unique<documentapi::MapVisitorMessage>();

    for (const auto & count : _schemeCount) {
        cmd->getData().set(vespalib::make_string("scheme.%s", count.first.c_str()), count.second);
    }

    for (const auto & count : _namespaceCount) {
        cmd->getData().set(vespalib::make_string("namespace.%s", count.first.c_str()), count.second);
    }

    for (const auto & count : _groupCount) {
        cmd->getData().set(vespalib::make_string("group.%s", count.first.c_str()), count.second);
    }

    for (const auto & count : _userCount) {
        cmd->getData().set(vespalib::make_string("user.%" PRIu64, count.first), count.second);
    }

    sendMessage(std::move(cmd));
}

}
