// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/exceptions.h>

class FastOS_FileInterface;

namespace search {

class SummaryException : public vespalib::IoException
{
public:
    SummaryException(std::string_view msg, FastOS_FileInterface & file, std::string_view location);
};

} // namespace search
