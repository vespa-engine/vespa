// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reference_operations.h"
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

namespace vespalib::eval {

namespace {

bool concat_address(const TensorSpec::Address &me, const TensorSpec::Address &other,
                    const std::string &concat_dim, size_t my_offset,
                    TensorSpec::Address &my_out, TensorSpec::Address &other_out)
{
    my_out.insert_or_assign(concat_dim, my_offset);
    for (const auto &my_dim: me) {
        const auto & name = my_dim.first;
        const auto & label = my_dim.second;
        if (name == concat_dim) {
            my_out.insert_or_assign(name, label.index + my_offset);
        } else {
            auto pos = other.find(name);
            if ((pos == other.end()) || (pos->second == label)) {
                my_out.insert_or_assign(name, label);
                other_out.insert_or_assign(name, label);
            } else {
                return false;
            }
        }
    }
    return true;
}

bool concat_addresses(const TensorSpec::Address &a, const TensorSpec::Address &b,
                      const std::string &concat_dim, size_t b_offset,
                      TensorSpec::Address &a_out, TensorSpec::Address &b_out)
{
    return concat_address(a, b, concat_dim,        0, a_out, b_out) &&
           concat_address(b, a, concat_dim, b_offset, b_out, a_out);
}

double value_from_child(const TensorSpec &child) {
    double sum = 0.0;
    for (const auto & [addr, value] : child.cells()) {
        sum += value;
    }
    return sum;
}

bool join_address(const TensorSpec::Address &a, const TensorSpec::Address &b, TensorSpec::Address &addr) {
    for (const auto &dim_a: a) {
        auto pos_b = b.find(dim_a.first);
        if ((pos_b != b.end()) && !(pos_b->second == dim_a.second)) {
            return false;
        }
        addr.insert_or_assign(dim_a.first, dim_a.second);
    }
    return true;
}

vespalib::string rename_dimension(const vespalib::string &name, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to) {
    for (size_t i = 0; i < from.size(); ++i) {
        if (name == from[i]) {
            return to[i];
        }
    }
    return name;
}

} // namespace <unnamed>


TensorSpec ReferenceOperations::concat(const TensorSpec &a, const TensorSpec &b, const std::string &concat_dim) {
    ValueType a_type = ValueType::from_spec(a.type());
    ValueType b_type = ValueType::from_spec(b.type());
    ValueType res_type = ValueType::concat(a_type, b_type, concat_dim);
    TensorSpec result(res_type.to_spec());
    if (res_type.is_error()) {
        return result;
    }
    size_t b_offset = 1;
    size_t concat_dim_index = a_type.dimension_index(concat_dim);
    if (concat_dim_index != ValueType::Dimension::npos) {
        const auto &dim = a_type.dimensions()[concat_dim_index];
        assert(dim.is_indexed()); // type resolving (above) should catch this
        b_offset = dim.size;
    }
    for (const auto &cell_a: a.cells()) {
        for (const auto &cell_b: b.cells()) {
            TensorSpec::Address addr_a;
            TensorSpec::Address addr_b;
            if (concat_addresses(cell_a.first, cell_b.first, concat_dim, b_offset, addr_a, addr_b)) {
                result.add(addr_a, cell_a.second);
                result.add(addr_b, cell_b.second);
            }
        }
    }
    return result;
}


TensorSpec ReferenceOperations::create(const vespalib::string &type, const CreateSpec &spec, const std::vector<TensorSpec> &children) {
    TensorSpec result(type);
    if (ValueType::from_spec(type).is_error()) {
        return result;
    }
    for (const auto & [addr, child_idx] : spec) {
        assert(child_idx < children.size());
        const auto &child = children[child_idx];
        double val = value_from_child(child);
        result.add(addr, val);
    }
    return result;
}


TensorSpec ReferenceOperations::join(const TensorSpec &a, const TensorSpec &b, join_fun_t function) {
    ValueType res_type = ValueType::join(ValueType::from_spec(a.type()), ValueType::from_spec(b.type()));
    TensorSpec result(res_type.to_spec());
    if (res_type.is_error()) {
        return result;
    }
    for (const auto &cell_a: a.cells()) {
        for (const auto &cell_b: b.cells()) {
            TensorSpec::Address addr;
            if (join_address(cell_a.first, cell_b.first, addr) &&
                join_address(cell_b.first, cell_a.first, addr))
            {
                result.add(addr, function(cell_a.second, cell_b.second));
            }
        }
    }
    return result;
}


TensorSpec ReferenceOperations::map(const TensorSpec &a, map_fun_t func) {
    ValueType res_type = ValueType::from_spec(a.type());
    TensorSpec result(res_type.to_spec());
    if (res_type.is_error()) {
        return result;
    }
    for (const auto & [ addr, value ]: a.cells()) {
        result.add(addr, func(value));
    }
    return result;
}


TensorSpec ReferenceOperations::merge(const TensorSpec &a, const TensorSpec &b, join_fun_t fun) {
    ValueType res_type = ValueType::merge(ValueType::from_spec(a.type()),
                                          ValueType::from_spec(b.type()));
    TensorSpec result(res_type.to_spec());
    if (res_type.is_error()) {
        return result;
    }
    for (const auto & [ addr, value ]: a.cells()) {
        auto other = b.cells().find(addr);
        if (other == b.cells().end()) {
            result.add(addr, value);
        } else {
            result.add(addr, fun(value, other->second));
        }
    }
    for (const auto & [ addr, value ]: b.cells()) {
        auto other = a.cells().find(addr);
        if (other == a.cells().end()) {
            result.add(addr, value);
        }
    }
    return result;
}


TensorSpec ReferenceOperations::peek(const TensorSpec &param, const PeekSpec &peek_spec, const std::vector<TensorSpec> &children) {
    if (peek_spec.empty()) {
        return TensorSpec(ValueType::error_type().to_spec());
    }
    std::vector<vespalib::string> peek_dims;
    for (const auto & [dim_name, label_or_child] : peek_spec) {
        peek_dims.push_back(dim_name);
    }
    ValueType param_type = ValueType::from_spec(param.type());
    ValueType result_type = param_type.reduce(peek_dims);
    TensorSpec result(result_type.to_spec());
    if (result_type.is_error()) {
        return result;
    }
    auto is_mapped_dim = [&](const vespalib::string &name) {
        size_t dim_idx = param_type.dimension_index(name);
        assert(dim_idx != ValueType::Dimension::npos);
        const auto &param_dim = param_type.dimensions()[dim_idx];
        return param_dim.is_mapped();
    };
    TensorSpec::Address addr;
    for (const auto & [dim_name, label_or_child] : peek_spec) {
        const vespalib::string &dim = dim_name;
        std::visit(vespalib::overload
                   {
                       [&](const TensorSpec::Label &label) {
                           addr.emplace(dim, label);
                       },
                       [&](const size_t &child_idx) {
                           assert(child_idx < children.size());
                           const auto &child = children[child_idx];
                           double child_value = value_from_child(child);
                           if (is_mapped_dim(dim)) {
                               addr.emplace(dim, vespalib::make_string("%zd", int64_t(child_value)));
                           } else {
                               addr.emplace(dim, child_value);
                           }
                       }
                   }, label_or_child);
    }
    for (const auto &cell: param.cells()) {
        bool keep = true;
        TensorSpec::Address my_addr;
        for (const auto &binding: cell.first) {
            auto pos = addr.find(binding.first);
            if (pos == addr.end()) {
                my_addr.emplace(binding.first, binding.second);
            } else {
                if (!(pos->second == binding.second)) {
                    keep = false;
                }
            }
        }
        if (keep) {
            result.add(my_addr, cell.second);
        }
    }
    return result;
}


TensorSpec ReferenceOperations::reduce(const TensorSpec &a, Aggr aggr, const std::vector<vespalib::string> &dims) {
    ValueType res_type = ValueType::from_spec(a.type()).reduce(dims);
    TensorSpec result(res_type.to_spec());
    if (res_type.is_error()) {
        return result;
    }
    Stash stash;
    std::map<TensorSpec::Address,std::optional<Aggregator*>> my_map;
    for (const auto &cell: a.cells()) {
        TensorSpec::Address addr;
        for (const auto &dim: cell.first) {
            if (res_type.dimension_index(dim.first) != ValueType::Dimension::npos) {
                addr.insert_or_assign(dim.first, dim.second);
            }
        }
        auto [pos, is_empty] = my_map.emplace(addr, std::nullopt);
        if (is_empty) {
            pos->second = &Aggregator::create(aggr, stash);
            pos->second.value()->first(cell.second);
        } else {
            pos->second.value()->next(cell.second);
        }
    }
    for (const auto &my_entry: my_map) {
        result.add(my_entry.first, my_entry.second.value()->result());
    }
    return result;
}


TensorSpec ReferenceOperations::rename(const TensorSpec &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to) {
    assert(from.size() == to.size());
    ValueType res_type = ValueType::from_spec(a.type()).rename(from, to);
    TensorSpec result(res_type.to_spec());
    if (res_type.is_error()) {
        return result;
    }
    for (const auto &cell: a.cells()) {
        TensorSpec::Address addr;
        for (const auto &dim: cell.first) {
            addr.insert_or_assign(rename_dimension(dim.first, from, to), dim.second);
        }
        result.add(addr, cell.second);
    }
    return result;
}


} // namespace
