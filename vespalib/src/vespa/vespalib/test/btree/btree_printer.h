// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "data_printer.h"
#include "aggregated_printer.h"
#include <vespa/vespalib/btree/btreenodeallocator.h>

namespace vespalib::btree::test {

template <typename ostream, typename NodeAllocator>
class BTreePrinter
{
    using LeafNode = typename NodeAllocator::LeafNodeType;
    using InternalNode = typename NodeAllocator::InternalNodeType;
    ostream &_os;
    const NodeAllocator &_allocator;
    bool _levelFirst;
    uint8_t _printLevel;

    void printLeafNode(const LeafNode &n) {
        if (!_levelFirst) {
            _os << ",";
        }
        _levelFirst = false;
        _os << "{";
        for (uint32_t i = 0; i < n.validSlots(); ++i) {
            if (i > 0) _os << ",";
            _os << n.getKey(i) << ":" << n.getData(i);
        }
        printAggregated(_os, n.getAggregated());
        _os << "}";
    }

    void printInternalNode(const InternalNode &n) {
        if (!_levelFirst) {
            _os << ",";
        }
        _levelFirst = false;
        _os << "{";
        for (uint32_t i = 0; i < n.validSlots(); ++i) {
            if (i > 0) _os << ",";
            _os << n.getKey(i);
        }
        printAggregated(_os, n.getAggregated());
        _os << "}";
    }

    void printNode(BTreeNode::Ref ref) {
        if (!ref.valid()) {
            _os << "[]";
        }
        if (_allocator.isLeafRef(ref)) {
            printLeafNode(*_allocator.mapLeafRef(ref));
            return;
        }
        const InternalNode &n(*_allocator.mapInternalRef(ref));
        if (n.getLevel() == _printLevel) {
            printInternalNode(n);
            return;
        }
        for (uint32_t i = 0; i < n.validSlots(); ++i) {
            printNode(n.getChild(i));
        }
    }

public:

    BTreePrinter(ostream &os, const NodeAllocator &allocator)
        : _os(os),
          _allocator(allocator),
          _levelFirst(true),
          _printLevel(0)
    {
    }

    ~BTreePrinter() { }

    void print(BTreeNode::Ref ref) {
        if (!ref.valid()) {
            _os << "{}";
            return;
        }
        _printLevel = 0;
        if (!_allocator.isLeafRef(ref)) {
            const InternalNode &n(*_allocator.mapInternalRef(ref));
            _printLevel = n.getLevel();
        }
        while (_printLevel > 0) {
            _os << "{";
            _levelFirst = true;
            printNode(ref);
            _os << "} -> ";
            --_printLevel;
        }
        _os << "{";
        _levelFirst = true;
        printNode(ref);
        _os << "}";
    }
};

} // namespace vespalib::btree::test
