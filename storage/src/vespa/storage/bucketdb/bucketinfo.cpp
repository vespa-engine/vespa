// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketinfo.h"
#include <vespa/storage/storageutil/utils.h>

namespace storage {

BucketInfo::BucketInfo()
    : _lastGarbageCollection(0)
{ }

BucketInfo::~BucketInfo() { }

std::string
BucketInfo::toString() const {
    std::ostringstream ost;
    print(ost, true, "");
    return ost.str();
}

bool
BucketInfo::emptyAndConsistent() const {
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        if (!_nodes[i].empty()) return false;
    }
    return consistentNodes();
}

bool
BucketInfo::validAndConsistent() const {
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        if (!_nodes[i].valid()) return false;
    }
    return consistentNodes();
}

bool
BucketInfo::hasInvalidCopy() const
{
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        if (!_nodes[i].valid()) return true;
    }
    return false;
}

void
BucketInfo::updateTrusted() {
    if (validAndConsistent()) {
        for (uint32_t i = 0; i < _nodes.size(); i++) {
            _nodes[i].setTrusted();
        }
    }

    int trusted = -1;
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        if (_nodes[i].trusted()) {
            trusted = i;
            break;
        }
    }

    if (trusted != -1) {
        for (uint32_t i = 0; i < _nodes.size(); i++) {
            if (_nodes[i].consistentWith(_nodes[trusted])) {
                _nodes[i].setTrusted();
            } else if (_nodes[i].trusted()) {
                resetTrusted();
                return;
            }
        }
    }
}

void
BucketInfo::resetTrusted() {
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        _nodes[i].clearTrusted();
    }
    updateTrusted();
}

uint16_t
BucketInfo::getTrustedCount() const {
    uint32_t trustedCount = 0;
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        if (_nodes[i].trusted()) {
            trustedCount++;
        }
    }
    return trustedCount;
}

bool
BucketInfo::consistentNodes(bool countInvalidAsConsistent) const
{
    int compareIndex = 0;
    for (uint32_t i=1; i<_nodes.size(); i++) {
        if (!_nodes[i].consistentWith(_nodes[compareIndex],
                                     countInvalidAsConsistent)) return false;
    }
    return true;
}

void
BucketInfo::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    if (_nodes.empty()) {
        out << "no nodes";
    }
    for (uint32_t i=0; i<_nodes.size(); ++i) {
        if (i != 0) out << ", ";
        _nodes[i].print(out, verbose, indent);
    }
}

namespace {

struct Sorter {
    const std::vector<uint16_t>& _order;

    Sorter(const std::vector<uint16_t>& recommendedOrder) :
        _order(recommendedOrder) {}

    bool operator() (const BucketCopy& a, const BucketCopy& b) {
        int order_a = -1;
        for (uint32_t i = 0; i < _order.size(); i++) {
            if (_order[i] == a.getNode()) {
                order_a = i;
                break;
            }
        }
        int order_b = -1;
        for (uint32_t i = 0; i < _order.size(); i++) {
            if (_order[i] == b.getNode()) {
                order_b = i;
                break;
            }
        }

        if (order_b == -1 && order_a == -1) {
            return a.getNode() < b.getNode();
        }
        if (order_b == -1) {
            return true;
        }
        if (order_a == -1) {
            return false;
        }

        return order_a < order_b;
    }
};

}

void
BucketInfo::updateNode(const BucketCopy& newCopy)
{
    BucketCopy* found = getNodeInternal(newCopy.getNode());

    if (found) {
        *found = newCopy;
        updateTrusted();
    }
}

void
BucketInfo::addNodes(const std::vector<BucketCopy>& newCopies,
                     const std::vector<uint16_t>& recommendedOrder,
                     TrustedUpdate update)
{
    for (uint32_t i = 0; i < newCopies.size(); ++i) {
        BucketCopy* found = getNodeInternal(newCopies[i].getNode());

        if (found) {
            if (found->getTimestamp() < newCopies[i].getTimestamp()) {
                found->setBucketInfo(newCopies[i].getTimestamp(),
                                     newCopies[i].getBucketInfo());
            }
        } else {
            _nodes.push_back(newCopies[i]);
        }
    }

    std::sort(_nodes.begin(), _nodes.end(), Sorter(recommendedOrder));

    if (update == TrustedUpdate::UPDATE) {
        updateTrusted();
    }
}

void
BucketInfo::addNode(const BucketCopy& newCopy,
                    const std::vector<uint16_t>& recommendedOrder)
{
    addNodes(toVector<BucketCopy>(newCopy),
             recommendedOrder);
}

bool
BucketInfo::removeNode(unsigned short node, TrustedUpdate update)
{
    for (std::vector<BucketCopy>::iterator iter = _nodes.begin();
         iter != _nodes.end();
         iter++) {
        if (iter->getNode() == node) {
            _nodes.erase(iter);
            if (update == TrustedUpdate::UPDATE) {
                updateTrusted();
            }
            return true;
        }
    }
    return false;
}

const BucketCopy*
BucketInfo::getNode(uint16_t node) const
{
    for (std::vector<BucketCopy>::const_iterator iter = _nodes.begin();
         iter != _nodes.end();
         iter++) {
        if (iter->getNode() == node) {
            return &*iter;
        }
    }
    return 0;
}

BucketCopy*
BucketInfo::getNodeInternal(uint16_t node)
{
    for (std::vector<BucketCopy>::iterator iter = _nodes.begin();
         iter != _nodes.end();
         iter++) {
        if (iter->getNode() == node) {
            return &*iter;
        }
    }
    return 0;
}

std::vector<uint16_t>
BucketInfo::getNodes() const {
    std::vector<uint16_t> result;

    for (uint32_t i = 0; i < _nodes.size(); i++) {
        result.push_back(_nodes[i].getNode());
    }

    return result;
}

uint32_t
BucketInfo::getHighestDocumentCount() const
{
    uint32_t highest = 0;
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        highest = std::max(highest, _nodes[i].getDocumentCount());
    }
    return highest;
}

uint32_t
BucketInfo::getHighestTotalDocumentSize() const
{
    uint32_t highest = 0;
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        highest = std::max(highest, _nodes[i].getTotalDocumentSize());
    }
    return highest;
}

uint32_t
BucketInfo::getHighestMetaCount() const
{
    uint32_t highest = 0;
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        highest = std::max(highest, _nodes[i].getMetaCount());
    }
    return highest;
}

uint32_t
BucketInfo::getHighestUsedFileSize() const
{
    uint32_t highest = 0;
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        highest = std::max(highest, _nodes[i].getUsedFileSize());
    }
    return highest;
}

bool
BucketInfo::hasRecentlyCreatedEmptyCopy() const
{
    for (uint32_t i = 0; i < _nodes.size(); ++i) {
        if (_nodes[i].wasRecentlyCreated()) {
            return true;
        }
    }
    return false;
}

bool
BucketInfo::operator==(const BucketInfo& other) const
{
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

std::ostream&
operator<<(std::ostream& out, const BucketInfo& info) {
    info.print(out, false, "");
    return out;
}

}
