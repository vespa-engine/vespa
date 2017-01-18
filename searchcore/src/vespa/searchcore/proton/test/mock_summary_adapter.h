// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/server/isummaryadapter.h>

namespace proton {

namespace test {

/**
 * Mock of the ISummaryAdapter interface used for unit testing.
 */
struct MockSummaryAdapter : public ISummaryAdapter
{
    virtual void put(search::SerialNum, const document::Document &, const search::DocumentIdT) override {}
    virtual void remove(search::SerialNum, const search::DocumentIdT) override {}
    virtual void heartBeat(search::SerialNum) override {}
    virtual const search::IDocumentStore &getDocumentStore() const override {
        const search::IDocumentStore *store = NULL;
        return *store;
    }
    virtual std::unique_ptr<document::Document> get(const search::DocumentIdT,
                                                    const document::DocumentTypeRepo &) override {
        return std::unique_ptr<document::Document>();
    }
    virtual void compactLidSpace(uint32_t wantedDocIdLimit) override {
        (void) wantedDocIdLimit;
    }
};

} // namespace test

} // namespace proton
