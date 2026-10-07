// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document.h"

#include <vespa/check_require.h>

#include <algorithm>

namespace proton::test {

/**
 * Collection of documents contained in the same bucket.
 */
class BucketDocuments {
private:
    DocumentVector _docs;

public:
    BucketDocuments() : _docs() {}
    document::BucketId getBucket() const {
        if (!_docs.empty()) {
            return _docs.back().getBucket();
        }
        return document::BucketId();
    }
    const DocumentVector& getDocs() const { return _docs; }
    DocumentVector getGidOrderDocs() const {
        DocumentVector retval = _docs;
        std::sort(retval.begin(), retval.end(), DocumentGidOrderCmp());
        return retval;
    }
    void addDoc(const Document& doc) {
        if (!_docs.empty()) {
            CHECK(_docs.back().getBucket() == doc.getBucket());
        }
        _docs.push_back(doc);
    }
};

} // namespace proton::test
