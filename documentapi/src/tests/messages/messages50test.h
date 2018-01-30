// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "testbase.h"

class Messages50Test : public TestBase {
protected:
    const vespalib::Version getVersion() const override { return vespalib::Version(5, 0); }
    bool shouldTestCoverage() const override { return false; }
    bool tryDocumentReply(const string &filename, uint32_t type);
    bool tryVisitorReply(const string &filename, uint32_t type);

public:
    Messages50Test();

    bool testBatchDocumentUpdateMessage();
    bool testBatchDocumentUpdateReply();
    bool testCreateVisitorMessage();
    bool testCreateVisitorReply();
    bool testDestroyVisitorMessage();
    bool testDestroyVisitorReply();
    bool testDocumentListMessage();
    bool testDocumentListReply();
    bool testDocumentSummaryMessage();
    bool testDocumentSummaryReply();
    bool testEmptyBucketsMessage();
    bool testEmptyBucketsReply();
    bool testGetBucketListMessage();
    bool testGetBucketListReply();
    bool testGetBucketStateMessage();
    bool testGetBucketStateReply();
    bool testGetDocumentMessage();
    bool testGetDocumentReply();
    bool testMapVisitorMessage();
    bool testMapVisitorReply();
    bool testMultiOperationMessage();
    bool testMultiOperationReply();
    bool testPutDocumentMessage();
    bool testPutDocumentReply();
    bool testQueryResultMessage();
    bool testQueryResultReply();
    bool testRemoveDocumentMessage();
    bool testRemoveDocumentReply();
    bool testRemoveLocationMessage();
    bool testRemoveLocationReply();
    bool testSearchResultMessage();
    bool testSearchResultReply();
    bool testStatBucketMessage();
    bool testStatBucketReply();
    bool testUpdateDocumentMessage();
    bool testUpdateDocumentReply();
    bool testVisitorInfoMessage();
    bool testVisitorInfoReply();
    bool testWrongDistributionReply();
};

