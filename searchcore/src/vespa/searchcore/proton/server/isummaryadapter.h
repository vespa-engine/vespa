// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

/**
 * Interface for a summary adapter.
 **/
class ISummaryAdapter {
public:
    typedef std::unique_ptr<ISummaryAdapter> UP;
    typedef std::shared_ptr<ISummaryAdapter> SP;

    virtual ~ISummaryAdapter() {}

    // feed interface
    virtual void put(search::SerialNum serialNum,
                     const document::Document &doc,
                     const search::DocumentIdT lid) = 0;
    virtual void remove(search::SerialNum serialNum,
                        const search::DocumentIdT lid) = 0;

    virtual void
    heartBeat(search::SerialNum serialNum) = 0;

    virtual const search::IDocumentStore &
    getDocumentStore() const = 0;

    virtual std::unique_ptr<document::Document>
    get(const search::DocumentIdT lid,
        const document::DocumentTypeRepo &repo) = 0;

    virtual void compactLidSpace(uint32_t wantedDocIdLimit) = 0;
};

} // namespace proton

