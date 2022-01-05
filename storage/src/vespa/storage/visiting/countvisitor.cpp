// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    for (size_t i = 0; i < entries.size(); ++i) {
        const spi::DocEntry& entry(*entries[i]);
        if (!entry.isRemove()) {
            const document::Document* doc = entry.getDocument();

            if (doc) {
                const document::IdString& idString = doc->getId().getScheme();
                hitCounter.addHit(doc->getId(), 0);

                if (_doNamespace) {
                    _namespaceCount[idString.getNamespace()]++;
                }

                if (_doUser && idString.hasNumber()) {
                    _userCount[idString.getNumber()]++;
                }

                if (_doGroup && idString.hasGroup()) {
                    _groupCount[idString.getGroup()]++;
                }

                if (_doScheme) {
                    _schemeCount["id"]++;
                }

            }
        }
    }
}

void CountVisitor::completedVisiting(HitCounter&) {
    documentapi::MapVisitorMessage* cmd(new documentapi::MapVisitorMessage());

    for (std::map<std::string, int>::iterator iter = _schemeCount.begin();
         iter != _schemeCount.end();
         iter++) {
        cmd->getData().set(vespalib::make_string("scheme.%s", iter->first.c_str()), iter->second);
    }

    for (NamespaceCountMap::const_iterator iter = _namespaceCount.begin();
         iter != _namespaceCount.end();
         iter++) {
        cmd->getData().set(vespalib::make_string("namespace.%s", iter->first.c_str()), iter->second);
    }

    for (GroupCountMap::const_iterator iter = _groupCount.begin();
         iter != _groupCount.end();
         iter++) {
        cmd->getData().set(vespalib::make_string("group.%s", iter->first.c_str()), iter->second);
    }

    for (std::map<uint64_t, int>::iterator iter = _userCount.begin();
         iter != _userCount.end();
         iter++) {
        cmd->getData().set(vespalib::make_string("user.%" PRIu64, iter->first), iter->second);
    }

    sendMessage(documentapi::DocumentMessage::UP(cmd));
}

}
