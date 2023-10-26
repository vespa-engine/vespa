// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketdatabase.h"
#include <sstream>

namespace storage {

BucketDatabase::Entry
BucketDatabase::getNext(const document::BucketId& last) const
{
    return upperBound(last);
}

BucketDatabase::Entry
BucketDatabase::createAppropriateBucket(
        uint16_t minBits, const document::BucketId& bid)
{
    document::BucketId newBid(getAppropriateBucket(minBits, bid));

    Entry e(newBid);
    update(e);
    return e;
}

template <typename BucketInfoType>
std::ostream& operator<<(std::ostream& o, const BucketDatabase::EntryBase<BucketInfoType>& e)
{
    if (!e.valid()) {
        o << "NONEXISTING";
    } else {
        o << e.getBucketId() << " : " << e.getBucketInfo();
    }
    return o;
}

template <typename BucketInfoType>
std::string
BucketDatabase::EntryBase<BucketInfoType>::toString() const
{
    std::ostringstream ost;
    ost << *this;
    return ost.str();
}

template std::ostream& operator<<(std::ostream& o, const BucketDatabase::Entry& e);
template std::ostream& operator<<(std::ostream& o, const BucketDatabase::ConstEntryRef& e);

template class BucketDatabase::EntryBase<BucketInfo>;
template class BucketDatabase::EntryBase<ConstBucketInfoRef>;

}
