// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryadapter.h"
#include <vespa/document/fieldvalue/stringfieldvalue.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.summaryadapter");

using namespace document;

namespace proton {

SummaryAdapter::SummaryAdapter(const SummaryManager::SP &mgr)
    : _mgr(mgr),
      _imgr(mgr),
      _lastSerial(_mgr->getBackingStore().lastSyncToken())
{
    // empty
}

bool SummaryAdapter::ignore(search::SerialNum serialNum) const
{
    assert(serialNum != 0);
    return serialNum <= _lastSerial;
}

void
SummaryAdapter::put(search::SerialNum serialNum,
                    const document::Document &doc,
                    const search::DocumentIdT lid)
{
    if ( ! ignore(serialNum) ) {
        LOG(spam, "SummaryAdapter::put(docId = '%s', lid = %u, document = '%s')",
            doc.getId().toString().c_str(), lid, doc.toString(true).c_str());
        _mgr->putDocument(serialNum, doc, lid);
        _lastSerial = serialNum;
    }
}

void
SummaryAdapter::remove(search::SerialNum serialNum,
                       const search::DocumentIdT lid)
{
    if ( ! ignore(serialNum + 1) ) {
        _mgr->removeDocument(serialNum, lid);
        _lastSerial = serialNum;
    }
}

void
SummaryAdapter::heartBeat(search::SerialNum serialNum)
{
    if (serialNum > _lastSerial) {
        remove(serialNum, 0u); // XXX: Misused lid 0
    }
}


} // namespace proton
