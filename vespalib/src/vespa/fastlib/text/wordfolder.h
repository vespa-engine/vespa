// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastlib/text/unicodeutil.h>

class Fast_WordFolder
{
public:
    virtual ~Fast_WordFolder() = default;
    virtual const char* UCS4Tokenize(const char *buf,
                                     const char *bufend,
                                     ucs4_t *dstbuf,
                                     ucs4_t *dstbufend,
                                     const char*& origstart,
                                     size_t& tokenlen) const = 0;
};
