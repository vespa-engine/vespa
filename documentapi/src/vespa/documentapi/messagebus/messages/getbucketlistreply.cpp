// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getbucketlistreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <ostream>

namespace documentapi {

GetBucketListReply::BucketInfo::BucketInfo() :
    _bucket(),
    _bucketInformation()
{ }

GetBucketListReply::BucketInfo::BucketInfo(const document::BucketId &bucketId,
                                           const string &bucketInformation) :
    _bucket(bucketId),
    _bucketInformation(bucketInformation)
{ }

bool
GetBucketListReply::BucketInfo::operator==(const GetBucketListReply::BucketInfo &rhs) const
{
    return _bucket == rhs._bucket && _bucketInformation == rhs._bucketInformation;
}

GetBucketListReply::GetBucketListReply() noexcept :
    DocumentReply(DocumentProtocol::REPLY_GETBUCKETLIST),
    _buckets()
{ }

GetBucketListReply::~GetBucketListReply() = default;

std::ostream &
operator<<(std::ostream &out, const GetBucketListReply::BucketInfo &info)
{
    return out << "BucketInfo(" << info._bucket << ": " << info._bucketInformation << ")";
}

}
