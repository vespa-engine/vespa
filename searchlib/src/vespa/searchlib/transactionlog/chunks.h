// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ichunk.h"

namespace search::transactionlog {

class XXH64None : public IChunk {
protected:
    void onEncode(nbostream &os) override;
    void onDecode(nbostream &is) override;
public:
};

class XXH64LZ4 : public IChunk {
protected:
    void onEncode(nbostream &os) override;
    void onDecode(nbostream &is) override;
public:
};

}
