// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once


namespace vespalib
{

class nbostream;

}


namespace search
{

namespace diskindex
{

enum class BitVectorKeyScope
{
    SHARED_WORDS,
    PERFIELD_WORDS
};

const char *getBitVectorKeyScopeSuffix(BitVectorKeyScope scope);

}

}

namespace vespalib
{

nbostream &
operator<<(nbostream &stream,
           const search::diskindex::BitVectorKeyScope &scope);

nbostream &
operator>>(nbostream &stream,
           search::diskindex::BitVectorKeyScope &scope);

}
