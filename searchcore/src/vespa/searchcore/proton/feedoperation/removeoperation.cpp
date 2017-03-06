// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.feedoperation.removeoperation");

#include "removeoperation.h"

using document::BucketId;
using document::DocumentId;
using document::DocumentTypeRepo;
using storage::spi::Timestamp;
using vespalib::make_string;

namespace proton {

RemoveOperation::RemoveOperation()
    : DocumentOperation(FeedOperation::REMOVE),
      _docId()
{
}


RemoveOperation::RemoveOperation(const BucketId &bucketId,
                                 const Timestamp &timestamp,
                                 const DocumentId &docId)
    : DocumentOperation(FeedOperation::REMOVE,
                        bucketId,
                        timestamp),
      _docId(docId)
{
}

void
RemoveOperation::serialize(vespalib::nbostream &os) const
{
    assertValidBucketId(_docId);
    DocumentOperation::serialize(os);
    size_t oldSize = os.size();
    vespalib::string rawId = _docId.toString();
    os.write(rawId.c_str(), rawId.size() + 1);
    _serializedDocSize = os.size() - oldSize;
}


void
RemoveOperation::deserialize(vespalib::nbostream &is,
                          const DocumentTypeRepo &repo)
{
    DocumentOperation::deserialize(is, repo);
    size_t oldSize = is.size();
    _docId = DocumentId(is);
    _serializedDocSize = oldSize - is.size();
}

vespalib::string RemoveOperation::toString() const {
    return make_string("Remove(%s, %s)",
                       _docId.getScheme().toString().c_str(),
                       docArgsToString().c_str());
}
} // namespace proton
