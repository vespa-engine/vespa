// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/input.h>
#include "taintable.h"

namespace vbench {

using Input = vespalib::Input;
using Memory = vespalib::Memory;

/**
 * A Taintable Input implementation reading sequentially from a memory
 * mapped file.
 **/
class MappedFileInput : public Input,
                        public Taintable
{
private:
    int    _file;
    char  *_data;
    size_t _size;
    Taint  _taint;
    size_t _pos;

public:
    MappedFileInput(const string &name);
    Memory get() const { return Memory(_data, _size); }
    virtual Memory obtain();
    virtual Input &evict(size_t bytes);
    virtual const Taint &tainted() const { return _taint; }
};

} // namespace vbench

