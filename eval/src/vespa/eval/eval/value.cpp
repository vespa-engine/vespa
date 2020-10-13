// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include "tensor_engine.h"
#include <vespa/vespalib/util/typify.h>

namespace vespalib {
namespace eval {

namespace {

struct TrivialView : Value::Index::View {
    bool first = false;
    void lookup(ConstArrayRef<const vespalib::stringref*> ) override { first = true; }
    bool next_result(ConstArrayRef<vespalib::stringref*> , size_t &idx_out) override {
        if (first) {
            idx_out = 0;
            first = false;
            return true;
        } else {
            return false;
        }
    }
};

struct MySum {
    template <typename CT> static double invoke(TypedCells cells) {
        double res = 0.0;
        for (CT cell: cells.typify<CT>()) {
            res += cell;
        }
        return res;
    }
};

} // <unnamed>


TrivialIndex::TrivialIndex() = default;
TrivialIndex TrivialIndex::_index;

size_t
TrivialIndex::size() const
{
    return 1;
}

std::unique_ptr<Value::Index::View>
TrivialIndex::create_view(const std::vector<size_t> &) const
{
    return std::make_unique<TrivialView>();
}

double
Value::as_double() const
{
    return typify_invoke<1,TypifyCellType,MySum>(type().cell_type(), cells());
}

ValueType DoubleValue::_type = ValueType::double_type();

} // namespace vespalib::eval
} // namespace vespalib
