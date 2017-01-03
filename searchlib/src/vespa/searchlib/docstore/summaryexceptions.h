// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/exceptions.h>

class FastOS_FileInterface;

namespace search {

class SummaryException : public vespalib::IoException
{
public:
    SummaryException(const vespalib::stringref &msg, FastOS_FileInterface & file, const vespalib::stringref &location);
};

} // namespace search
