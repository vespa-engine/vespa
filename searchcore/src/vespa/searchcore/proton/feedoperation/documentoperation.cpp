// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.feedoperation.documentoperation");

#include "documentoperation.h"
#include <vespa/vespalib/util/stringfmt.h>

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
      _prevTimestamp()
{
}


DocumentOperation::DocumentOperation(Type type,
                                     const BucketId &bucketId,
                                     const Timestamp &timestamp)
    : FeedOperation(type),
      _bucketId(bucketId),
      _timestamp(timestamp),
      _dbdId(),
      _prevDbdId(),
      _prevMarkedAsRemoved(false),
      _prevTimestamp()
{
}


DocumentOperation::DocumentOperation(Type type,
                                     const document::BucketId &bucketId,
                                     const storage::spi::Timestamp &timestamp,
                                     SerialNum serialNum,
                                     DbDocumentId dbdId,
                                     DbDocumentId prevDbdId)
    : FeedOperation(type),
      _bucketId(bucketId),
      _timestamp(timestamp),
      _dbdId(dbdId),
      _prevDbdId(prevDbdId),
      _prevMarkedAsRemoved(false),
      _prevTimestamp()
{
    setSerialNum(serialNum);
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
DocumentOperation::serialize(vespalib::nbostream &os) const
{
    os << _bucketId;
    os << _timestamp;
    os << _dbdId;
    os << _prevDbdId;
    os << _prevMarkedAsRemoved;
    os << _prevTimestamp;
}


void
DocumentOperation::deserialize(vespalib::nbostream &is,
                               const DocumentTypeRepo &)
{
    is >> _bucketId;
    is >> _timestamp;
    is >> _dbdId;
    is >> _prevDbdId;
    is >> _prevMarkedAsRemoved;
    is >> _prevTimestamp;
}

} // namespace proton
