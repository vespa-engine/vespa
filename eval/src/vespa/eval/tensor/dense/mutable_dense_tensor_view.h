// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <cassert>

namespace vespalib::eval {

/**
 * A dense tensor with a cells reference that can be modified.
 */
class MutableDenseTensorView : public Value {
private:
    const ValueType _type;
    TypedCells _cells;
public:
    MutableDenseTensorView(const ValueType &type_in);
    void setCells(TypedCells cells_in) {
        _cells = cells_in;
    }
    const ValueType &type() const final override { return _type; }
    TypedCells cells() const final override { return _cells; }
    const Index &index() const final override { return TrivialIndex::get(); }
    MemoryUsage get_memory_usage() const final override { return self_memory_usage<MutableDenseTensorView>(); }
};

}
