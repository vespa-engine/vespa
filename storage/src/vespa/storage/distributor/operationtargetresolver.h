// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \brief Interface to deduct what bucket copies to send load to.
 *
 * - Must handle inconsistent split buckets.
 */
#pragma once

#include <vespa/document/bucket/bucket.h>
#include <vespa/vdslib/state/node.h>
#include <vespa/vespalib/util/printable.h>

namespace storage::distributor {

class OperationTarget : public vespalib::AsciiPrintable
{
    document::Bucket _bucket;
    lib::Node        _node;
    bool             _newCopy;

public:
    OperationTarget() noexcept : _newCopy(true) {}
    OperationTarget(const document::Bucket& bucket, const lib::Node& node, bool newCopy) noexcept
        : _bucket(bucket), _node(node), _newCopy(newCopy) {}

    document::BucketId getBucketId() const noexcept { return _bucket.getBucketId(); }
    document::Bucket getBucket() const noexcept { return _bucket; }
    const lib::Node& getNode() const noexcept { return _node; }
    bool isNewCopy() const noexcept { return _newCopy; }

    bool operator==(const OperationTarget& o) const noexcept {
        return (_bucket == o._bucket && _node == o._node && _newCopy == o._newCopy);
    }
    bool operator!=(const OperationTarget& o) const noexcept {
        return !(operator==(o));
    }

    void print(vespalib::asciistream& out, const PrintProperties&) const override;
};

class OperationTargetList : public std::vector<OperationTarget> {
public:
    bool hasAnyNewCopies() const noexcept {
        for (size_t i=0; i<size(); ++i) {
            if (operator[](i).isNewCopy()) return true;
        }
        return false;
    }
    bool hasAnyExistingCopies() const noexcept {
        for (size_t i=0; i<size(); ++i) {
            if (!operator[](i).isNewCopy()) return true;
        }
        return false;
    }
};

class OperationTargetResolver {
public:
    virtual ~OperationTargetResolver() = default;

    // Sadly all operations but put currently implement this by themselves.
    enum OperationType {
        PUT
    };
    
    virtual OperationTargetList getTargets(OperationType type, const document::BucketId& id) = 0;
};

}
