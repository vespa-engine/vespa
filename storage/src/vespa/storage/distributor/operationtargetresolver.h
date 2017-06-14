// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \brief Interface to deduct what bucket copies to send load to.
 *
 * - Must handle inconsistent split buckets.
 */
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/vdslib/state/node.h>
#include <vespa/vespalib/util/printable.h>

namespace storage {
namespace distributor {

class OperationTarget : public vespalib::AsciiPrintable
{
    document::BucketId _bucket;
    lib::Node _node;
    bool _newCopy;

public:
    OperationTarget() : _newCopy(true) {} 
    OperationTarget(const document::BucketId& id, const lib::Node& node, bool newCopy)
        : _bucket(id), _node(node), _newCopy(newCopy) {}

    const document::BucketId& getBucketId() const { return _bucket; }
    const lib::Node& getNode() const { return _node; }
    bool isNewCopy() const { return _newCopy; }

    bool operator==(const OperationTarget& o) const {
        return (_bucket == o._bucket && _node == o._node && _newCopy == o._newCopy);
    }
    bool operator!=(const OperationTarget& o) const {
        return !(operator==(o));
    }

    void print(vespalib::asciistream& out, const PrintProperties&) const override;
};

class OperationTargetList : public std::vector<OperationTarget> {
public:
    bool hasAnyNewCopies() const {
        for (size_t i=0; i<size(); ++i) {
            if (operator[](i).isNewCopy()) return true;
        }
        return false;
    }
    bool hasAnyExistingCopies() const {
        for (size_t i=0; i<size(); ++i) {
            if (!operator[](i).isNewCopy()) return true;
        }
        return false;
    }
};

class OperationTargetResolver {
public:
    virtual ~OperationTargetResolver() {}

    // Sadly all operations but put currently implement this by themselves.
    enum OperationType {
        PUT
    };
    
    virtual OperationTargetList getTargets(OperationType type,
                                           const document::BucketId& id) = 0;
};

} // distributor
} // storage
