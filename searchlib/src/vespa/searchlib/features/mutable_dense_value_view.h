// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <cassert>

namespace search::features::mutable_value {

using namespace vespalib::eval;

/**
 * A dense tensor with a cells reference that can be modified.
 */
class MutableDenseValueView : public Value {
private:
    const ValueType _type;
    TypedCells _cells;
public:
    MutableDenseValueView(const ValueType &type_in);
    void setCells(TypedCells cells_in) {
        assert(cells_in.type == _type.cell_type());
        _cells = cells_in;
    }
    const ValueType &type() const final override { return _type; }
    TypedCells cells() const final override { return _cells; }
    const Index &index() const final override { return TrivialIndex::get(); }
    vespalib::MemoryUsage get_memory_usage() const final override {
        return self_memory_usage<MutableDenseValueView>();
    }
};

}
