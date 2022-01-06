// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testvisitor.h"
#include <vespa/persistence/spi/docentry.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".visitor.instance.testvisitor");

namespace storage {

TestVisitor::TestVisitor(StorageComponent& c,
                         const vdslib::Parameters& params)
    : Visitor(c),
      _params()
{
    std::ostringstream ost;
    for (vdslib::Parameters::ParametersMap::const_iterator
             it(params.begin()), mt(params.end()); it != mt; ++it)
    {
        ost << "\n  " << it->first << " = " << it->second.c_str();
    }
    _params = ost.str();
    LOG(debug, "Created TestVisitor: %s", _params.c_str());
}

void
TestVisitor::startingVisitor(const std::vector<document::BucketId>& buckets)
{
    std::ostringstream ost;
    ost << "Starting visitor with given parameters:" << _params << "\n"
        << "Visiting the following bucket time intervals:\n";
    for (uint32_t i=0, n=buckets.size(); i<n; ++i) {
        ost << "  " << buckets[i] << "\n";
    }
    LOG(debug, "%s", ost.str().c_str());
    report(ost.str());
}

void
TestVisitor::handleDocuments(const document::BucketId& /*bucketId*/,
                             DocEntryList & entries,
                             HitCounter& /*hitCounter*/)
{
    std::ostringstream ost;
    ost << "Handling block of " << entries.size() << " documents.\n";
    LOG(debug, "%s", ost.str().c_str());
    report(ost.str());
}

void TestVisitor::completedBucket(const document::BucketId& bucket, HitCounter&)
{
    std::ostringstream ost;
    ost << "completedBucket(" << bucket.getId() << ")\n";
    LOG(debug, "%s", ost.str().c_str());
    report(ost.str());
}

void TestVisitor::completedVisiting(HitCounter&)
{
    LOG(debug, "completedVisiting()");
    report("completedVisiting()\n");
}

void TestVisitor::abortedVisiting()
{
    LOG(debug, "abortedVisiting()");
    report("abortedVisiting()\n");
}

void TestVisitor::report(const std::string& message) {
    // As we have no existing way of sending a single message back to the
    // client, use a map visitor command
    documentapi::MapVisitorMessage* cmd = new documentapi::MapVisitorMessage();
    cmd->getData().set("msg", message);
    sendMessage(documentapi::DocumentMessage::UP(cmd));
}

}
