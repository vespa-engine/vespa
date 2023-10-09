// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketdocuments.h"
#include <map>

namespace proton::test {

/**
 * Collection of documents for a set of users,
 * where each user is located in the same bucket.
 */
class UserDocuments
{
public:
    using DocMap = std::map<uint32_t, BucketDocuments>;
    using Iterator = DocMap::const_iterator;
private:
    DocMap _docs;
public:
    UserDocuments()
        : _docs()
    {
    }
    void merge(const UserDocuments &rhs) {
        _docs.insert(rhs._docs.begin(), rhs._docs.end());
    }
    void addDoc(uint32_t userId, const Document &userDoc) {
        _docs[userId].addDoc(userDoc);
    }
    const BucketDocuments &getUserDocs(uint32_t userId) const {
        auto itr = _docs.find(userId);
        assert(itr != _docs.end());
        return itr->second;
    }
    document::BucketId getBucket(uint32_t userId) const {
        return getUserDocs(userId).getBucket();
    }
    const DocumentVector &getDocs(uint32_t userId) const {
        return getUserDocs(userId).getDocs();
    }
    DocumentVector getGidOrderDocs(uint32_t userId) const {
        return getUserDocs(userId).getGidOrderDocs();
    }
    Iterator begin() const { return _docs.begin(); }
    Iterator end() const { return _docs.end(); }
    void clear() { _docs.clear(); }
};

}
