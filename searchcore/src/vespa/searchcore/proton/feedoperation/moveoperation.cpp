// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.feedoperation.moveoperation");

#include "moveoperation.h"
#include <vespa/vespalib/util/stringfmt.h>

using document::BucketId;
using document::Document;
using document::DocumentTypeRepo;
using storage::spi::Timestamp;
using vespalib::make_string;

namespace proton {

MoveOperation::MoveOperation()
    : DocumentOperation(FeedOperation::MOVE),
      _doc()
{
}


MoveOperation::MoveOperation(const BucketId &bucketId,
                             const Timestamp &timestamp,
                             const Document::SP &doc,
                             DbDocumentId sourceDbdId,
                             uint32_t targetSubDbId)
    : DocumentOperation(FeedOperation::MOVE, bucketId, timestamp),
      _doc(doc)
{
    setPrevDbDocumentId(sourceDbdId);
    setDbDocumentId(DbDocumentId(targetSubDbId, 0u));
}


void
MoveOperation::serialize(vespalib::nbostream &os) const
{
    assertValidBucketId(_doc->getId());
    assert(movingLidIfInSameSubDb());
    DocumentOperation::serialize(os);
    _doc->serialize(os);
}


void
MoveOperation::deserialize(vespalib::nbostream &is,
                           const DocumentTypeRepo &repo)
{
    DocumentOperation::deserialize(is, repo);
    _doc.reset(new Document(repo, is));
}

vespalib::string MoveOperation::toString() const {
    return make_string("Move(%s, %s)",
                       _doc.get() ?
                       _doc->getId().getScheme().toString().c_str() : "NULL",
                       docArgsToString().c_str());
}

} // namespace proton
