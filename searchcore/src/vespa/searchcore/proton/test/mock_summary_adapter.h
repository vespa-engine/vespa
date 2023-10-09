// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/server/isummaryadapter.h>

namespace proton::test {

/**
 * Mock of the ISummaryAdapter interface used for unit testing.
 */
struct MockSummaryAdapter : public ISummaryAdapter
{
    void put(SerialNum, DocumentIdT, const Document &) override {}
    void put(SerialNum, DocumentIdT, const vespalib::nbostream &) override {}
    void remove(SerialNum, DocumentIdT) override {}
    void heartBeat(SerialNum) override {}
    const search::IDocumentStore &getDocumentStore() const override {
        const search::IDocumentStore *store = NULL;
        return *store;
    }
    std::unique_ptr<document::Document> get(DocumentIdT, const DocumentTypeRepo &) override {
        return std::unique_ptr<document::Document>();
    }
    void compactLidSpace(uint32_t wantedDocIdLimit) override {
        (void) wantedDocIdLimit;
    }
};

}
