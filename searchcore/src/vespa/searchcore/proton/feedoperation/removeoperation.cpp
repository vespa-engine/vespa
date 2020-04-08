// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removeoperation.h"

using document::BucketId;
using document::DocumentId;
using document::DocumentTypeRepo;
using storage::spi::Timestamp;
using vespalib::make_string;

namespace proton {

RemoveOperationWithDocId::RemoveOperationWithDocId()
    : RemoveOperation(FeedOperation::REMOVE),
      _docId()
{
}


RemoveOperationWithDocId::RemoveOperationWithDocId(BucketId bucketId, Timestamp timestamp, const DocumentId &docId)
    : RemoveOperation(FeedOperation::REMOVE, bucketId, timestamp),
      _docId(docId)
{
}

RemoveOperationWithDocId::~RemoveOperationWithDocId() = default;

void
RemoveOperationWithDocId::serialize(vespalib::nbostream &os) const
{
    assertValidBucketId(_docId);
    RemoveOperation::serialize(os);
    size_t oldSize = os.size();
    vespalib::string rawId = _docId.toString();
    os.write(rawId.c_str(), rawId.size() + 1);
    _serializedDocSize = os.size() - oldSize;
}


void
RemoveOperationWithDocId::deserialize(vespalib::nbostream &is,
                          const DocumentTypeRepo &repo)
{
    RemoveOperation::deserialize(is, repo);
    size_t oldSize = is.size();
    _docId = DocumentId(is);
    _serializedDocSize = oldSize - is.size();
}

vespalib::string
RemoveOperationWithDocId::toString() const {
    return make_string("Remove(%s, %s)",
                       _docId.getScheme().toString().c_str(),
                       docArgsToString().c_str());
}

} // namespace proton
