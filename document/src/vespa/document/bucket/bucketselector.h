// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::BucketSelector
 * \ingroup bucket
 *
 * \brief Calculates which buckets correspond to a document selection.
 *
 * When you want to visit a subset of documents in VDS you specify a
 * document selection expression. Some of these expressions limit what
 * buckets may contain matching documents.
 *
 * This class is used to calculate which set of buckets we need to visit
 * to be sure we find all existing data.
 *
 * \see BucketId For more information on buckets
 * \see document::select::Parser For more information about the selection
 * language
 */

#pragma once

#include <memory>
#include <vector>

namespace document {
namespace select {
    class Node;
}
class BucketId;
class BucketIdFactory;

class BucketSelector {
    const BucketIdFactory& _factory;

public:
    explicit BucketSelector(const BucketIdFactory& factory);

    using BucketVector = std::vector<BucketId>;
    /**
     * Get a list of bucket ids that needs to be visited to be sure to find
     * all data matching given expression. Note that we can only detect
     * some common expressions. We guarantuee that you get all buckets
     * that may contain data, but not that you get the minimal bucket set.
     *
     * If a small bucket set can not be identified, a null pointer is returned
     * to indicate all buckets needs to be visited.
     */
    std::unique_ptr<BucketVector> select(const select::Node& expression) const;
};

} // document
