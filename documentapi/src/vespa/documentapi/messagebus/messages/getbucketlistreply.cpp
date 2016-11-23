// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/getbucketlistreply.h>

namespace documentapi {

GetBucketListReply::BucketInfo::BucketInfo() :
    _bucket(),
    _bucketInformation()
{
    // empty
}

GetBucketListReply::BucketInfo::BucketInfo(const document::BucketId &bucketId,
                                           const string &bucketInformation) :
    _bucket(bucketId),
    _bucketInformation(bucketInformation)
{
    // empty
}

bool
GetBucketListReply::BucketInfo::operator==(const GetBucketListReply::BucketInfo &rhs) const
{
    return _bucket == rhs._bucket && _bucketInformation == rhs._bucketInformation;
}

GetBucketListReply::GetBucketListReply() :
    DocumentReply(DocumentProtocol::REPLY_GETBUCKETLIST),
    _buckets()
{
    // empty
}

std::ostream &
operator<<(std::ostream &out, const GetBucketListReply::BucketInfo &info)
{
    return out << "BucketInfo(" << info._bucket << ": " << info._bucketInformation << ")";
}

}
