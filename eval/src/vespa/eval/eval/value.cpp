// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include "value_codec.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/objects/nbostream.h>

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

template <typename T>
ValueType ScalarValue<T>::_type = ValueType::make_type(get_cell_type<T>(), {});

template class ScalarValue<double>;
template class ScalarValue<float>;

std::unique_ptr<Value>
ValueBuilderFactory::copy(const Value &value) const
{
    nbostream stream;
    encode_value(value, stream);
    return decode_value(stream, *this);
}

} // namespace vespalib::eval
} // namespace vespalib
