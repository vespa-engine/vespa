// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_spec.h"
#include "array_array_map.h"
#include "function.h"
#include "interpreted_function.h"
#include "value.h"
#include "value_codec.h"
#include "value_type.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/eval/eval/test/reference_evaluation.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <ostream>

namespace vespalib {
namespace eval {

namespace {

TensorSpec::Address extract_address(const slime::Inspector &address) {
    struct Extractor : slime::ObjectTraverser {
        TensorSpec::Address address;
        void field(const Memory &dimension, const slime::Inspector &label) override {
            if (label.type().getId() == slime::STRING::ID) {
                address.emplace(dimension.make_string(), TensorSpec::Label(label.asString().make_string()));
            } else if (label.type().getId() == slime::LONG::ID) {
                address.emplace(dimension.make_string(), TensorSpec::Label(label.asLong()));
            }
        }
    };
    Extractor extractor;
    address.traverse(extractor);
    return extractor.address;
}

struct NormalizeTensorSpec {
    /*
     * This is basically value_from_spec() + spec_from_value()
     * implementation, taken from value_codec.cpp
     */
    template <typename T>
    static TensorSpec invoke(const ValueType &type, const TensorSpec &spec) {
        size_t dense_size = type.dense_subspace_size();
        size_t num_mapped_dims = type.count_mapped_dimensions();
        size_t max_subspaces = std::max(spec.cells().size() / dense_size, size_t(1));
        ArrayArrayMap<vespalib::stringref,T> map(num_mapped_dims, dense_size, max_subspaces);
        std::vector<vespalib::stringref> sparse_key;
        for (const auto &entry: spec.cells()) {
            sparse_key.clear();
            size_t dense_key = 0;
            auto binding = entry.first.begin();
            for (const auto &dim : type.dimensions()) {
                assert(binding != entry.first.end());
                assert(dim.name == binding->first);
                assert(dim.is_mapped() == binding->second.is_mapped());
                if (dim.is_mapped()) {
                    sparse_key.push_back(binding->second.name);
                } else {
                    assert(binding->second.index < dim.size);
                    dense_key = (dense_key * dim.size) + binding->second.index;
                }
                ++binding;
            }
            assert(binding == entry.first.end());
            assert(dense_key < map.values_per_entry());
            auto [tag, ignore] = map.lookup_or_add_entry(ConstArrayRef<vespalib::stringref>(sparse_key));
            map.get_values(tag)[dense_key] = entry.second;
        }
        // if spec is missing the required dense space, add it here:
        if ((map.keys_per_entry() == 0) && (map.size() == 0)) {
            map.add_entry(ConstArrayRef<vespalib::stringref>());
        }
        TensorSpec result(type.to_spec());
        map.each_entry([&](const auto &keys, const auto &values)
                       {
                           auto sparse_addr_iter = keys.begin();
                           TensorSpec::Address address;
                           for (const auto &dim : type.dimensions()) {
                               if (dim.is_mapped()) {
                                   address.emplace(dim.name, *sparse_addr_iter++);
                               }
                           }
                           assert(sparse_addr_iter == keys.end());
                           for (size_t i = 0; i < values.size(); ++i) {
                               size_t dense_key = i;
                               for (auto dim = type.dimensions().rbegin();
                                    dim != type.dimensions().rend();
                                    ++dim)
                               {
                                   if (dim->is_indexed()) {
                                       size_t label = dense_key % dim->size;
                                       address.emplace(dim->name, label).first->second = TensorSpec::Label(label);
                                       dense_key /= dim->size;
                                   }
                               }
                               result.add(address, values[i]);
                           }
                       });
        return result;
    }
};

} // namespace vespalib::eval::<unnamed>


TensorSpec::TensorSpec(const vespalib::string &type_spec)
    : _type(type_spec),
      _cells()
{ }

TensorSpec::TensorSpec(const TensorSpec &) = default;
TensorSpec & TensorSpec::operator = (const TensorSpec &) = default;

TensorSpec::~TensorSpec() { }

double
TensorSpec::as_double() const
{
    double result = 0.0;
    for (const auto &[key, value]: _cells) {
        result += value.value;
    }
    return result;
}

TensorSpec &
TensorSpec::add(Address address, double value) {
    auto [iter, inserted] = _cells.emplace(std::move(address), value);
    if (! inserted) {
        // to simplify reference implementations, allow
        // adding the same address several times to a Spec, but
        // only with the same value every time:
        assert(iter->second == Value(value));
    }
    return *this;
}

vespalib::string
TensorSpec::to_string() const
{
    vespalib::string out = make_string("spec(%s) {\n", _type.c_str());
    for (const auto &cell: _cells) {
        size_t n = 0;
        out.append("  [");
        for (const auto &label: cell.first) {
            if (n++) {
                out.append(",");
            }
            if (label.second.is_mapped()) {
                out.append(label.second.name);
            } else {
                out.append(make_string("%zu", label.second.index));
            }
        }
        out.append(make_string("]: %g\n", cell.second.value));
    }
    out.append("}");
    return out;
}

void
TensorSpec::to_slime(slime::Cursor &tensor) const
{
    tensor.setString("type", _type);
    slime::Cursor &cells = tensor.setArray("cells");
    for (const auto &my_cell: _cells) {
        slime::Cursor &cell = cells.addObject();
        slime::Cursor &address = cell.setObject("address");
        for (const auto &label: my_cell.first) {
            if (label.second.is_mapped()) {
                address.setString(label.first, label.second.name);
            } else {
                address.setLong(label.first, label.second.index);
            }
        }
        cell.setDouble("value", my_cell.second.value);
    }
}

TensorSpec
TensorSpec::from_slime(const slime::Inspector &tensor)
{
    TensorSpec spec(tensor["type"].asString().make_string());
    const slime::Inspector &cells = tensor["cells"];
    for (size_t i = 0; i < cells.entries(); ++i) {
        const slime::Inspector &cell = cells[i];
        Address address = extract_address(cell["address"]);
        spec.add(address, cell["value"].asDouble());
    }
    return spec;
}

TensorSpec
TensorSpec::from_value(const eval::Value &value)
{
    return spec_from_value(value);
}

TensorSpec
TensorSpec::from_expr(const vespalib::string &expr)
{
    auto fun = Function::parse(expr);
    if (!fun->has_error() && (fun->num_params() == 0)) {
        return test::ReferenceEvaluation::eval(*fun, {});
    }
    return TensorSpec("error");
}

bool
operator==(const TensorSpec &lhs, const TensorSpec &rhs)
{
    return ((lhs.type() == rhs.type()) &&
            (lhs.cells() == rhs.cells()));
}

std::ostream &
operator<<(std::ostream &out, const TensorSpec &spec)
{
    out << spec.to_string();
    return out;
}

TensorSpec
TensorSpec::normalize() const
{
    ValueType my_type = ValueType::from_spec(type());
    if (my_type.is_error()) {
        return TensorSpec(my_type.to_spec());
    }
    return typify_invoke<1,TypifyCellType,NormalizeTensorSpec>(my_type.cell_type(), my_type, *this);
}


} // namespace vespalib::eval
} // namespace vespalib
