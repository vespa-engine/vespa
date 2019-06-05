// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketcopy.h"
#include <vespa/vespalib/util/arrayref.h>
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
