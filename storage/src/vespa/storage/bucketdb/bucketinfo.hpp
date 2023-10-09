// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketcopy.h"
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <iostream>
#include <sstream>

namespace storage {

template <typename NodeSeq>
std::string
BucketInfoBase<NodeSeq>::toString() const {
    std::ostringstream ost;
    print(ost, true, "");
    return ost.str();
}

template <typename NodeSeq>
bool
BucketInfoBase<NodeSeq>::emptyAndConsistent() const noexcept {
    for (const auto & n : _nodes) {
        if (!n.empty()) {
            return false;
        }
    }
    return consistentNodes();
}

template <typename NodeSeq>
bool
BucketInfoBase<NodeSeq>::validAndConsistent() const noexcept {
    for (const auto & n : _nodes) {
        if (!n.valid()) {
            return false;
        }
    }
    return consistentNodes();
}

template <typename NodeSeq>
bool
BucketInfoBase<NodeSeq>::hasInvalidCopy() const noexcept {
    for (const auto & n : _nodes){
        if (!n.valid()) {
            return true;
        }
    }
    return false;
}

template <typename NodeSeq>
uint16_t
BucketInfoBase<NodeSeq>::getTrustedCount() const noexcept {
    uint32_t trustedCount = 0;
    for (const auto & n : _nodes) {
        if (n.trusted()) {
            trustedCount++;
        }
    }
    return trustedCount;
}

template <typename NodeSeq>
bool
BucketInfoBase<NodeSeq>::consistentNodes() const noexcept {
    int compareIndex = 0;
    for (uint32_t i = 1; i < _nodes.size(); i++) {
        if (!_nodes[i].consistentWith(_nodes[compareIndex])) return false;
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

constexpr bool
is_majority(size_t n, size_t m) noexcept {
    return (n >= (m / 2) + 1);
}

}

template <typename NodeSeq>
api::BucketInfo
BucketInfoBase<NodeSeq>::majority_consistent_bucket_info() const noexcept {
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
void
BucketInfoBase<NodeSeq>::print(std::ostream& out, bool verbose, const std::string& indent) const {
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
const BucketCopy*
BucketInfoBase<NodeSeq>::getNode(uint16_t node) const noexcept {
    for (const auto& n : _nodes) {
        if (n.getNode() == node) {
            return &n;
        }
    }
    return nullptr;
}

template <typename NodeSeq>
uint16_t
BucketInfoBase<NodeSeq>::internal_entry_index(uint16_t node) const noexcept {
    for (uint16_t i = 0; i < _nodes.size(); i++) {
        if (_nodes[i].getNode() == node) {
            return i;
        }
    }
    return 0xffff; // Not found signal
}

template <typename NodeSeq>
std::vector<uint16_t>
BucketInfoBase<NodeSeq>::getNodes() const noexcept {
    std::vector<uint16_t> result;
    result.reserve(_nodes.size());
    for (const auto & n : _nodes) {
        result.emplace_back(n.getNode());
    }
    return result;
}

template <typename NodeSeq>
typename BucketInfoBase<NodeSeq>::Highest
BucketInfoBase<NodeSeq>::getHighest() const noexcept {
    Highest highest;
    for (const auto & n : _nodes) {
        highest.update(n);
    }
    return highest;
}

template <typename NodeSeq>
uint32_t
BucketInfoBase<NodeSeq>::getHighestDocumentCount() const noexcept {
    uint32_t highest = 0;
    for (const auto & n : _nodes) {
        highest = std::max(highest, n.getDocumentCount());
    }
    return highest;
}

template <typename NodeSeq>
uint32_t
BucketInfoBase<NodeSeq>::getHighestMetaCount() const noexcept {
    uint32_t highest = 0;
    for (const auto & n : _nodes) {
        highest = std::max(highest, n.getMetaCount());
    }
    return highest;
}

template <typename NodeSeq>
uint32_t
BucketInfoBase<NodeSeq>::getHighestUsedFileSize() const noexcept {
    uint32_t highest = 0;
    for (const auto & n : _nodes) {
        highest = std::max(highest, n.getUsedFileSize());
    }
    return highest;
}

template <typename NodeSeq>
bool
BucketInfoBase<NodeSeq>::hasRecentlyCreatedEmptyCopy() const noexcept {
    for (const auto & n : _nodes) {
        if (n.wasRecentlyCreated()) {
            return true;
        }
    }
    return false;
}

template <typename NodeSeq>
bool
BucketInfoBase<NodeSeq>::operator==(const BucketInfoBase<NodeSeq>& other) const noexcept {
    if (_nodes.size() != other._nodes.size()) {
        return false;
    }

    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        if (_nodes[i].getNode() != other._nodes[i].getNode()) {
            return false;
        }

        if (_nodes[i] != other._nodes[i]) {
            return false;
        }
    }

    return true;
}

}
