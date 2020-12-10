// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <cassert>

namespace search::features {

/**
 * A dense tensor with a cells reference that can be modified.
 */
class MutableDenseTensorView : public vespalib::eval::Value {
private:
    const vespalib::eval::ValueType _type;
    vespalib::eval::TypedCells _cells;
public:
    MutableDenseTensorView(const vespalib::eval::ValueType &type_in);
    void setCells(vespalib::eval::TypedCells cells_in) {
        assert(cells_in.type == _type.cell_type());
        _cells = cells_in;
    }
    const vespalib::eval::ValueType &type() const final override { return _type; }
    vespalib::eval::TypedCells cells() const final override { return _cells; }
    const Index &index() const final override { return vespalib::eval::TrivialIndex::get(); }
    vespalib::MemoryUsage get_memory_usage() const final override {
        return vespalib::eval::self_memory_usage<MutableDenseTensorView>();
    }
};

}
