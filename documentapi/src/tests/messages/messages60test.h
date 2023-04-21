// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#pragma once

#include "testbase.h"

class Messages60Test : public TestBase {
protected:
    const vespalib::Version getVersion() const override { return vespalib::Version(6, 221); }
    bool shouldTestCoverage() const override { return true; }
    bool tryDocumentReply(const string &filename, uint32_t type);
    bool tryVisitorReply(const string &filename, uint32_t type);

    static size_t serializedLength(const string & str) { return sizeof(int32_t) + str.size(); }

public:
    Messages60Test();

    bool testCreateVisitorMessage();
    bool testCreateVisitorReply();
    bool testDestroyVisitorMessage();
    bool testDestroyVisitorReply();
    bool testDocumentIgnoredReply();
    bool testDocumentListMessage();
    bool testDocumentListReply();
    bool testDocumentSummaryMessage();
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
    bool testPutDocumentMessage();
    bool testPutDocumentReply();
    bool testQueryResultMessage();
    bool testQueryResultReply();
    bool testRemoveDocumentMessage();
    bool testRemoveDocumentReply();
    bool testRemoveLocationMessage();
    bool testRemoveLocationReply();
    bool testSearchResultMessage();
    bool testStatBucketMessage();
    bool testStatBucketReply();
    bool testUpdateDocumentMessage();
    bool testUpdateDocumentReply();
    bool testVisitorInfoMessage();
    bool testVisitorInfoReply();
    bool testWrongDistributionReply();
};

