// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentoperation.h"
#include <vespa/document/base/documentid.h>
#include <cassert>

using document::BucketId;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::GlobalId;
using storage::spi::Timestamp;
using vespalib::make_string;

namespace proton {

DocumentOperation::DocumentOperation(Type type)
    : FeedOperation(type),
      _bucketId(),
      _timestamp(),
      _dbdId(),
      _prevDbdId(),
      _prevMarkedAsRemoved(false),
      _prevTimestamp(),
      _serializedDocSize(0)
{
}


DocumentOperation::DocumentOperation(Type type, const BucketId &bucketId, const Timestamp &timestamp)
    : FeedOperation(type),
      _bucketId(bucketId),
      _timestamp(timestamp),
      _dbdId(),
      _prevDbdId(),
      _prevMarkedAsRemoved(false),
      _prevTimestamp(),
      _serializedDocSize(0)
{
}

void
DocumentOperation::assertValidBucketId(const document::DocumentId &docId) const
{
    assert(_bucketId.valid());
    uint8_t bucketUsedBits = _bucketId.getUsedBits();
    const GlobalId &gid = docId.getGlobalId();
    BucketId verId(gid.convertToBucketId());
    verId.setUsedBits(bucketUsedBits);
    assert(_bucketId.getRawId() == verId.getRawId() ||
           _bucketId.getRawId() == verId.getId());
}

vespalib::string DocumentOperation::docArgsToString() const {
    return make_string("%s, timestamp=%" PRIu64 ", dbdId=(%s), prevDbdId=(%s), "
                       "prevMarkedAsRemoved=%s, prevTimestamp=%" PRIu64 ", serialNum=%" PRIu64,
                       _bucketId.toString().c_str(), _timestamp.getValue(),
                       _dbdId.toString().c_str(), _prevDbdId.toString().c_str(),
                       (_prevMarkedAsRemoved ? "true" : "false"),
                       _prevTimestamp.getValue(), getSerialNum());
}

void
DocumentOperation::serialize(vespalib::nbostream &os) const {
    serializeDocumentOperationOnly(os);
}

void
DocumentOperation::serializeDocumentOperationOnly(vespalib::nbostream &os) const
{
    os << _bucketId;
    os << _timestamp;
    os << _dbdId;
    os << _prevDbdId;
    os << _prevMarkedAsRemoved;
    os << _prevTimestamp;
}


void
DocumentOperation::deserialize(vespalib::nbostream &is, const DocumentTypeRepo &)
{
    is >> _bucketId;
    is >> _timestamp;
    is >> _dbdId;
    is >> _prevDbdId;
    is >> _prevMarkedAsRemoved;
    is >> _prevTimestamp;
}

    DbDocumentId DocumentOperation::getDbDocumentId() const {
        return _dbdId;
    }

} // namespace proton
