// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace vespalib::eval {

namespace {

struct EmptyView : Value::Index::View {
    void lookup(ConstArrayRef<const string_id*> ) override {}
    bool next_result(ConstArrayRef<string_id*> , size_t &) override { return false; }
};

struct TrivialView : Value::Index::View {
    bool first = false;
    void lookup(ConstArrayRef<const string_id*> ) override { first = true; }
    bool next_result(ConstArrayRef<string_id*> , size_t &idx_out) override {
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

EmptyIndex::EmptyIndex() = default;
EmptyIndex EmptyIndex::_index;

size_t
EmptyIndex::size() const
{
    return 0;
}

std::unique_ptr<Value::Index::View>
EmptyIndex::create_view(ConstArrayRef<size_t>) const
{
    return std::make_unique<EmptyView>();
}

TrivialIndex::TrivialIndex() = default;
TrivialIndex TrivialIndex::_index;

size_t
TrivialIndex::size() const
{
    return 1;
}

std::unique_ptr<Value::Index::View>
TrivialIndex::create_view(ConstArrayRef<size_t>) const
{
    return std::make_unique<TrivialView>();
}

double
Value::as_double() const
{
    return typify_invoke<1,TypifyCellType,MySum>(type().cell_type(), cells());
}

ValueType DoubleValue::_type = ValueType::double_type();

}
