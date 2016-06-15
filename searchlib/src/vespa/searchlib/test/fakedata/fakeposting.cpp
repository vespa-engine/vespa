// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fakeposting");
#include "fakeposting.h"

namespace search
{

namespace fakedata
{

FakePosting::FakePosting(const std::string &name)
    : _name(name)
{
}


FakePosting::~FakePosting(void)
{
}


size_t
FakePosting::skipBitSize(void) const
{
    return l1SkipBitSize() + l2SkipBitSize() + l3SkipBitSize() +
        l4SkipBitSize();
}

size_t
FakePosting::l1SkipBitSize(void) const
{
    return 0;
}


size_t
FakePosting::l2SkipBitSize(void) const
{
    return 0;
}


size_t
FakePosting::l3SkipBitSize(void) const
{
    return 0;
}


size_t
FakePosting::l4SkipBitSize(void) const
{
    return 0;
}

} // namespace fakedata

} // namespace search
