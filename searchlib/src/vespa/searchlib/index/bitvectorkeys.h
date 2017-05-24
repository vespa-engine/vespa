// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

namespace search
{

namespace index
{

class BitVectorWordSingleKey
{
public:
    uint64_t _wordNum;
    uint32_t _numDocs;
    uint32_t _pad;

    BitVectorWordSingleKey()
        : _wordNum(0),
          _numDocs(0),
          _pad(0)
    {
    }

    bool
    operator<(const BitVectorWordSingleKey &rhs) const
    {
        return  _wordNum < rhs._wordNum;
    }

    bool
    operator==(const BitVectorWordSingleKey &rhs) const
    {
        return _wordNum == rhs._wordNum;
    }
};

} // namespace index

} // namespace search

