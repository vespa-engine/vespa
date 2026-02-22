// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

class FRT_ISharedBlob {
public:
    virtual void        addRef() = 0;
    virtual void        subRef() = 0;
    virtual uint32_t    getLen() = 0;
    virtual const char* getData() = 0;

protected:
    virtual ~FRT_ISharedBlob() = default;
};
