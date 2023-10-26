// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketinfo.h"
#include "bucketinfo.hpp"
#include <vespa/storage/storageutil/utils.h>
#include <algorithm>

namespace storage {

template class BucketInfoBase<std::vector<BucketCopy>>;
template class BucketInfoBase<vespalib::ConstArrayRef<BucketCopy>>;

BucketInfo::BucketInfo() noexcept : BucketInfoBase() {}

BucketInfo::BucketInfo(uint32_t lastGarbageCollection, std::vector<BucketCopy> nodes) noexcept
    : BucketInfoBase(lastGarbageCollection, std::move(nodes))
{}

BucketInfo::~BucketInfo() = default;

BucketInfo::BucketInfo(const BucketInfo&) = default;
BucketInfo& BucketInfo::operator=(const BucketInfo&) = default;
BucketInfo::BucketInfo(BucketInfo&&) noexcept = default;
BucketInfo& BucketInfo::operator=(BucketInfo&&) noexcept = default;

void
BucketInfo::updateTrusted() noexcept {
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
BucketInfo::resetTrusted() noexcept {
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        _nodes[i].clearTrusted();
    }
    updateTrusted();
}

namespace {

struct Sorter {
    const std::vector<uint16_t>& _order;

    Sorter(const std::vector<uint16_t>& recommendedOrder) noexcept :
        _order(recommendedOrder) {}

    bool operator() (const BucketCopy& a, const BucketCopy& b) noexcept {
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
                found->setBucketInfo(newCopies[i].getTimestamp(), newCopies[i].getBucketInfo());
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
BucketInfo::addNode(const BucketCopy& newCopy, const std::vector<uint16_t>& recommendedOrder)
{
    addNodes(toVector<BucketCopy>(newCopy), recommendedOrder);
}

bool
BucketInfo::removeNode(unsigned short node, TrustedUpdate update)
{
    for (auto iter = _nodes.begin(); iter != _nodes.end(); ++iter) {
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

BucketCopy*
BucketInfo::getNodeInternal(uint16_t node)
{
    for (BucketCopy & copy : _nodes) {
        if (copy.getNode() == node) {
            return &copy;
        }
    }
    return 0;
}

}
