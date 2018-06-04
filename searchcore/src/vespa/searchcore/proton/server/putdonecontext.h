// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operationdonecontext.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/serialnum.h>

namespace document { class Document; }

namespace proton {

class DocIdLimit;
class IGidToLidChangeHandler;

/**
 * Context class for document put operations that acks operation when
 * instance is destroyed. Typically a shared pointer to an instance is
 * passed around to multiple worker threads that performs portions of
 * a larger task before dropping the shared pointer, triggering the
 * ack when all worker threads have completed.
 */
class PutDoneContext : public OperationDoneContext
{
    uint32_t _lid;
    DocIdLimit *_docIdLimit;
    IGidToLidChangeHandler &_gidToLidChangeHandler;
    document::GlobalId _gid;
    search::SerialNum _serialNum;
    bool _enableNotifyPut;
    std::shared_ptr<const document::Document> _doc;

public:
    PutDoneContext(FeedToken token, IGidToLidChangeHandler &gidToLidChangeHandler,
                   std::shared_ptr<const document::Document> doc,
                   const document::GlobalId &gid, uint32_t lid, search::SerialNum serialNum, bool enableNotifyPut);
    ~PutDoneContext() override;

    void registerPutLid(DocIdLimit *docIdLimit) { _docIdLimit = docIdLimit; }
};


}  // namespace proton
