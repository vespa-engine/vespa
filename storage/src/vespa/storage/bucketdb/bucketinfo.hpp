// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketcopy.h"
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <iostream>
#include <sstream>

namespace storage {

template <typename NodeSeq>
std::string BucketInfoBase<NodeSeq>::toString() const {
    std::ostringstream ost;
    print(ost, true, "");
    return ost.str();
}

template <typename NodeSeq>
bool BucketInfoBase<NodeSeq>::emptyAndConsistent() const {
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        if (!_nodes[i].empty()) {
            return false;
        }
    }
    return consistentNodes();
}

template <typename NodeSeq>
bool BucketInfoBase<NodeSeq>::validAndConsistent() const {
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        if (!_nodes[i].valid()) {
            return false;
        }
    }
    return consistentNodes();
}

template <typename NodeSeq>
bool BucketInfoBase<NodeSeq>::hasInvalidCopy() const {
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        if (!_nodes[i].valid()) {
            return true;
        }
    }
    return false;
}

template <typename NodeSeq>
uint16_t BucketInfoBase<NodeSeq>::getTrustedCount() const {
    uint32_t trustedCount = 0;
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        if (_nodes[i].trusted()) {
            trustedCount++;
        }
    }
    return trustedCount;
}

template <typename NodeSeq>
bool BucketInfoBase<NodeSeq>::consistentNodes(bool countInvalidAsConsistent) const {
    int compareIndex = 0;
    for (uint32_t i = 1; i < _nodes.size(); i++) {
        if (!_nodes[i].consistentWith(_nodes[compareIndex],
                                      countInvalidAsConsistent)) return false;
    }
    return true;
}

namespace {

// BucketInfo wrapper which only concerns itself with fields that indicate
// whether replicas are in sync.
struct ReplicaMetadata {
    api::BucketInfo info;

    ReplicaMetadata() noexcept = default;
    explicit ReplicaMetadata(const api::BucketInfo& info_) noexcept
        : info(info_)
    {}
    bool operator==(const ReplicaMetadata& rhs) const noexcept {
        // TODO merge state checker itself only considers checksum, should we do the same...?
        return ((info.getChecksum() == rhs.info.getChecksum()) &&
                (info.getDocumentCount() == rhs.info.getDocumentCount()));
    }
    struct hash {
        size_t operator()(const ReplicaMetadata& rm) const noexcept {
            // We assume that just using the checksum is extremely likely to be unique in the table.
            return rm.info.getChecksum();
        }
    };
};

constexpr bool is_majority(size_t n, size_t m) {
    return (n >= (m / 2) + 1);
}

}

template <typename NodeSeq>
api::BucketInfo BucketInfoBase<NodeSeq>::majority_consistent_bucket_info() const noexcept {
    if (_nodes.size() < 3) {
        return {};
    }
    vespalib::hash_map<ReplicaMetadata, uint16_t, ReplicaMetadata::hash> meta_tracker;
    for (const auto& n : _nodes) {
        if (n.valid()) {
            meta_tracker[ReplicaMetadata(n.getBucketInfo())]++;
        }
    }
    for (const auto& meta : meta_tracker) {
        if (is_majority(meta.second, _nodes.size())) {
            return meta.first.info;
        }
    }
    return {};
}

template <typename NodeSeq>
void BucketInfoBase<NodeSeq>::print(std::ostream& out, bool verbose, const std::string& indent) const {
    if (_nodes.size() == 0) {
        out << "no nodes";
    }
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        if (i != 0) {
            out << ", ";
        }
        _nodes[i].print(out, verbose, indent);
    }
}

template <typename NodeSeq>
const BucketCopy* BucketInfoBase<NodeSeq>::getNode(uint16_t node) const {
    for (const auto& n : _nodes) {
        if (n.getNode() == node) {
            return &n;
        }
    }
    return 0;
}

template <typename NodeSeq>
std::vector<uint16_t> BucketInfoBase<NodeSeq>::getNodes() const {
    std::vector<uint16_t> result;
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        result.emplace_back(_nodes[i].getNode());
    }
    return result;
}

template <typename NodeSeq>
uint32_t BucketInfoBase<NodeSeq>::getHighestDocumentCount() const {
    uint32_t highest = 0;
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        highest = std::max(highest, _nodes[i].getDocumentCount());
    }
    return highest;
}

template <typename NodeSeq>
uint32_t BucketInfoBase<NodeSeq>::getHighestTotalDocumentSize() const {
    uint32_t highest = 0;
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        highest = std::max(highest, _nodes[i].getTotalDocumentSize());
    }
    return highest;
}

template <typename NodeSeq>
uint32_t BucketInfoBase<NodeSeq>::getHighestMetaCount() const {
    uint32_t highest = 0;
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        highest = std::max(highest, _nodes[i].getMetaCount());
    }
    return highest;
}

template <typename NodeSeq>
uint32_t BucketInfoBase<NodeSeq>::getHighestUsedFileSize() const {
    uint32_t highest = 0;
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        highest = std::max(highest, _nodes[i].getUsedFileSize());
    }
    return highest;
}

template <typename NodeSeq>
bool BucketInfoBase<NodeSeq>::hasRecentlyCreatedEmptyCopy() const {
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        if (_nodes[i].wasRecentlyCreated()) {
            return true;
        }
    }
    return false;
}

template <typename NodeSeq>
bool BucketInfoBase<NodeSeq>::operator==(const BucketInfoBase<NodeSeq>& other) const {
    if (_nodes.size() != other._nodes.size()) {
        return false;
    }

    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        if (_nodes[i].getNode() != other._nodes[i].getNode()) {
            return false;
        }

        if (!(_nodes[i] == other._nodes[i])) {
            return false;
        }
    }

    return true;
};

}
