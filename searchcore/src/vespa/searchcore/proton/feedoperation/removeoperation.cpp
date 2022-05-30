// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removeoperation.h"
#include <cassert>

using document::BucketId;
using document::DocumentId;
using document::GlobalId;
using document::DocumentTypeRepo;
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
RemoveOperationWithDocId::deserialize(vespalib::nbostream &is, const DocumentTypeRepo &repo)
{
    RemoveOperation::deserialize(is, repo);
    size_t oldSize = is.size();
    _docId = DocumentId(is);
    _serializedDocSize = oldSize - is.size();
}

vespalib::string
RemoveOperationWithDocId::toString() const {
    return make_string("Remove(%s, %s)",
                       _docId.getScheme().toString().c_str(), docArgsToString().c_str());
}

RemoveOperationWithGid::RemoveOperationWithGid()
    : RemoveOperation(FeedOperation::REMOVE_GID),
      _gid(),
      _docType()
{}


RemoveOperationWithGid::RemoveOperationWithGid(BucketId bucketId, Timestamp timestamp, const GlobalId &gid, vespalib::stringref docType)
    : RemoveOperation(FeedOperation::REMOVE_GID, bucketId, timestamp),
      _gid(gid),
      _docType(docType)
{}

RemoveOperationWithGid::~RemoveOperationWithGid() = default;

void
RemoveOperationWithGid::serialize(vespalib::nbostream &os) const
{
    assertValidBucketId(_gid);
    assert( ! getValidDbdId());
    RemoveOperation::serialize(os);
    size_t oldSize = os.size();
    os.write(_gid.get(), GlobalId::LENGTH);
    os.writeSmallString(_docType);
    _serializedDocSize = os.size() - oldSize;
}


void
RemoveOperationWithGid::deserialize(vespalib::nbostream &is, const DocumentTypeRepo &repo)
{
    RemoveOperation::deserialize(is, repo);
    size_t oldSize = is.size();
    char buf[GlobalId::LENGTH];
    is.read(buf, sizeof(buf));
    _gid.set(buf);
    is.readSmallString(_docType);
    _serializedDocSize = oldSize - is.size();
}

vespalib::string
RemoveOperationWithGid::toString() const {
    return make_string("RemoveGid(%s, %s, %s)",
                       _gid.toString().c_str(), _docType.c_str(), docArgsToString().c_str());
}

} // namespace proton
