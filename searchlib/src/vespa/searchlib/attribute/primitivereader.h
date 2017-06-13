// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "readerbase.h"
#include <vespa/searchlib/util/fileutil.h>

namespace search {

    template <typename T>
    class PrimitiveReader : public ReaderBase
    {
    public:
        PrimitiveReader(AttributeVector &attr)
            : ReaderBase(attr),
              _datReader(*_datFile)
        { }

        virtual ~PrimitiveReader() { }
        T getNextData() { return _datReader.readHostOrder(); }
        size_t getDataCount() const { return getDataCountHelper(sizeof(T)); }
    private:
        FileReader<T> _datReader;
    };

}

