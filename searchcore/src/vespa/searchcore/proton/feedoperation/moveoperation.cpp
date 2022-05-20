// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "moveoperation.h"
#include <vespa/document/fieldvalue/document.h>
#include <cassert>

using document::BucketId;
using document::Document;
using document::DocumentTypeRepo;
using storage::spi::Timestamp;
using vespalib::make_string;

namespace proton {

MoveOperation::MoveOperation()
    : DocumentOperation(FeedOperation::MOVE),
      _doc()
{ }


MoveOperation::MoveOperation(const BucketId &bucketId,
                             Timestamp timestamp,
                             const Document::SP &doc,
                             DbDocumentId sourceDbdId,
                             uint32_t targetSubDbId)
    : DocumentOperation(FeedOperation::MOVE, bucketId, timestamp),
      _doc(doc)
{
    setPrevDbDocumentId(sourceDbdId);
    setDbDocumentId(DbDocumentId(targetSubDbId, 0u));
}

MoveOperation::~MoveOperation() = default;

void
MoveOperation::serialize(vespalib::nbostream &os) const
{
    assertValidBucketId(_doc->getId());
    assert(movingLidIfInSameSubDb());
    DocumentOperation::serialize(os);
    size_t oldSize = os.size();
    _doc->serialize(os);
    _serializedDocSize = os.size() - oldSize;
}


void
MoveOperation::deserialize(vespalib::nbostream &is,
                           const DocumentTypeRepo &repo)
{
    DocumentOperation::deserialize(is, repo);
    size_t oldSize = is.size();
    _doc.reset(new Document(repo, is));
    _serializedDocSize = oldSize - is.size();
}

vespalib::string MoveOperation::toString() const {
    return make_string("Move(%s, %s)",
                       _doc.get() ?
                       _doc->getId().getScheme().toString().c_str() : "NULL",
                       docArgsToString().c_str());
}

} // namespace proton
