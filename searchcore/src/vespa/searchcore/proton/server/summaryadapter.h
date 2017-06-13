// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include "isummaryadapter.h"

namespace proton {

class SummaryAdapter : public ISummaryAdapter {
private:
    SummaryManager::SP _mgr;
    ISummaryManager::SP _imgr;
    search::SerialNum _lastSerial;

    bool ignore(search::SerialNum serialNum) const;

public:
    SummaryAdapter(const SummaryManager::SP &mgr);

    /**
     * Implements ISummaryAdapter.
     */
    virtual void put(search::SerialNum serialNum,
                     const document::Document &doc,
                     const search::DocumentIdT lid) override;
    virtual void remove(search::SerialNum serialNum,
                        const search::DocumentIdT lid) override;

    virtual void heartBeat(search::SerialNum serialNum) override;

    virtual const search::IDocumentStore &getDocumentStore() const override {
        return _imgr->getBackingStore();
    }

    virtual std::unique_ptr<document::Document> get(const search::DocumentIdT lid,
                                                    const document::DocumentTypeRepo &repo) override {
        return _imgr->getBackingStore().read(lid, repo);
    }

    virtual void compactLidSpace(uint32_t wantedDocIdLimit) override {
        _mgr->getBackingStore().compactLidSpace(wantedDocIdLimit);
    }
};

} // namespace proton

