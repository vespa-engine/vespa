// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "putoperation.h"
#include <vespa/document/fieldvalue/document.h>

using document::BucketId;
using document::Document;
using document::DocumentTypeRepo;
using storage::spi::Timestamp;
using vespalib::make_string;

namespace proton {

PutOperation::PutOperation()
    : DocumentOperation(FeedOperation::PUT),
      _doc()
{ }


PutOperation::PutOperation(const BucketId &bucketId,
                           const Timestamp &timestamp,
                           const Document::SP &doc)
    : DocumentOperation(FeedOperation::PUT,
                        bucketId,
                        timestamp),
      _doc(doc)
{ }

PutOperation::~PutOperation() { }

void
PutOperation::serialize(vespalib::nbostream &os) const
{
    assertValidBucketId(_doc->getId());
    DocumentOperation::serialize(os);
    size_t oldSize = os.size();
    _doc->serialize(os);
    _serializedDocSize = os.size() - oldSize;
}


void
PutOperation::deserialize(vespalib::nbostream &is,
                          const DocumentTypeRepo &repo)
{
    DocumentOperation::deserialize(is, repo);
    size_t oldSize = is.size();
    _doc.reset(new Document(repo, is));
    _serializedDocSize = oldSize - is.size();
}

vespalib::string
PutOperation::toString() const
{
    return make_string("Put(%s, %s)",
                       _doc.get() ?
                       _doc->getId().getScheme().toString().c_str() : "NULL",
                       docArgsToString().c_str());
}

void
PutOperation::assertValid() const
{
    assertValidBucketId(_doc->getId());
}

} // namespace proton
