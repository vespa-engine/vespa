// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketdatabase.h"
#include <sstream>

namespace storage {

namespace {
    struct GetNextEntryProcessor : public BucketDatabase::EntryProcessor {
        BucketDatabase::Entry _entry;

        bool process(const BucketDatabase::Entry& e) override {
            _entry = e;
            return false;
        }
    };
}

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

std::ostream& operator<<(std::ostream& o, const BucketDatabase::Entry& e)
{
    if (!e.valid()) {
        o << "NONEXISTING";
    } else {
        o << e.getBucketId() << " : " << e.getBucketInfo();
    }
    return o;
}

std::string
BucketDatabase::Entry::toString() const
{
    std::ostringstream ost;
    ost << *this;
    return ost.str();
}

}
