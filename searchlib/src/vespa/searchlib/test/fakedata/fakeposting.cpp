// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakeposting.h"

namespace search::fakedata {

FakePosting::FakePosting(const std::string &name)
    : _name(name)
{
}


FakePosting::~FakePosting()
{
}


size_t
FakePosting::skipBitSize() const
{
    return l1SkipBitSize() + l2SkipBitSize() + l3SkipBitSize() +
        l4SkipBitSize();
}

size_t
FakePosting::l1SkipBitSize() const
{
    return 0;
}


size_t
FakePosting::l2SkipBitSize() const
{
    return 0;
}


size_t
FakePosting::l3SkipBitSize() const
{
    return 0;
}


size_t
FakePosting::l4SkipBitSize() const
{
    return 0;
}

bool
FakePosting::has_interleaved_features() const
{
    return false;
}

bool
FakePosting::enable_unpack_normal_features() const
{
    return true;
}

bool
FakePosting::enable_unpack_interleaved_features() const
{
    return true;
}

}
