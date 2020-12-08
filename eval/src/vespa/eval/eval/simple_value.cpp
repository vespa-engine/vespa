// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_value.h"
#include "inline_operation.h"
#include "value_codec.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".eval.simple_value");

namespace vespalib::eval {

//-----------------------------------------------------------------------------

namespace {

struct CreateSimpleValueBuilderBase {
    template <typename T> static std::unique_ptr<ValueBuilderBase> invoke(const ValueType &type,
            size_t num_mapped_dims, size_t subspace_size, size_t expected_subspaces)
    {
        assert(check_cell_type<T>(type.cell_type()));
        return std::make_unique<SimpleValueT<T>>(type, num_mapped_dims, subspace_size, expected_subspaces);
    }
};

//-----------------------------------------------------------------------------

// look up a full address in the map directly
struct SimpleLookupView : public Value::Index::View {

    using Labels = std::vector<vespalib::string>;
    using Map = std::map<Labels, size_t>;

    const Map &map;
    Labels my_addr;
    Map::const_iterator pos;

    SimpleLookupView(const Map &map_in, size_t num_dims)
        : map(map_in), my_addr(num_dims, ""), pos(map.end()) {}

    void lookup(ConstArrayRef<const vespalib::stringref*> addr) override {
        assert(addr.size() == my_addr.size());
        for (size_t i = 0; i < my_addr.size(); ++i) {
            my_addr[i] = *addr[i];
        }
        pos = map.find(my_addr);
    }

    bool next_result(ConstArrayRef<vespalib::stringref*>, size_t &idx_out) override {
        if (pos == map.end()) {
            return false;
        }
        idx_out = pos->second;
        pos = map.end();
        return true;
    }
};

//-----------------------------------------------------------------------------

// find matching mappings for a partial address with brute force filtering
struct SimpleFilterView : public Value::Index::View {

    using Labels = std::vector<vespalib::string>;
    using Map = std::map<Labels, size_t>;

    const Map &map;
    std::vector<size_t> match_dims;
    std::vector<size_t> extract_dims;
    std::vector<vespalib::string> query;
    Map::const_iterator pos;

    bool is_match() const {
        for (size_t i = 0; i < query.size(); ++i) {
            if (query[i] != pos->first[match_dims[i]]) {
                return false;
            }
        }
        return true;
    }

    SimpleFilterView(const Map &map_in, const std::vector<size_t> &match_dims_in, size_t num_dims)
        : map(map_in), match_dims(match_dims_in), extract_dims(), query(match_dims.size(), ""), pos(map.end())
    {
        auto my_pos = match_dims.begin();
        for (size_t i = 0; i < num_dims; ++i) {
            if ((my_pos == match_dims.end()) || (*my_pos != i)) {
                extract_dims.push_back(i);
            } else {
                ++my_pos;
            }
        }
        assert(my_pos == match_dims.end());
        assert((match_dims.size() + extract_dims.size()) == num_dims);
    }

    void lookup(ConstArrayRef<const vespalib::stringref*> addr) override {
        assert(addr.size() == query.size());
        for (size_t i = 0; i < addr.size(); ++i) {
            query[i] = *addr[i];
        }
        pos = map.begin();
    }

    bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) override {
        while (pos != map.end()) {
            if (is_match()) {
                assert(addr_out.size() == extract_dims.size());
                for (size_t i = 0; i < extract_dims.size(); ++i) {
                    *addr_out[i] = pos->first[extract_dims[i]];
                }
                idx_out = pos->second;
                ++pos;
                return true;
            }
            ++pos;
        }
        return false;
    }
};

//-----------------------------------------------------------------------------

// iterate all mappings
struct SimpleIterateView : public Value::Index::View {

    using Labels = std::vector<vespalib::string>;
    using Map = std::map<Labels, size_t>;

    const Map &map;
    Map::const_iterator pos;

    SimpleIterateView(const Map &map_in)
        : map(map_in), pos(map.end()) {}

    void lookup(ConstArrayRef<const vespalib::stringref*>) override {
        pos = map.begin();
    }

    bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) override {
        if (pos == map.end()) {
            return false;
        }
        assert(addr_out.size() == pos->first.size());
        for (size_t i = 0; i < addr_out.size(); ++i) {
            *addr_out[i] = pos->first[i];
        }
        idx_out = pos->second;
        ++pos;
        return true;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

//-----------------------------------------------------------------------------

SimpleValue::SimpleValue(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in)
    : _type(type),
      _num_mapped_dims(num_mapped_dims_in),
      _subspace_size(subspace_size_in),
      _index()
{
    assert(_type.count_mapped_dimensions() == _num_mapped_dims);
    assert(_type.dense_subspace_size() == _subspace_size);
}

SimpleValue::~SimpleValue() = default;

void
SimpleValue::add_mapping(ConstArrayRef<vespalib::stringref> addr)
{
    Labels my_addr;
    for(const auto &label: addr) {
        my_addr.emplace_back(label);
    }
    auto [ignore, was_inserted] = _index.emplace(my_addr, _index.size());
    assert(was_inserted);
}

MemoryUsage
SimpleValue::estimate_extra_memory_usage() const
{
    using Node = std::map<Labels,size_t>::value_type;
    size_t key_extra_size = sizeof(vespalib::string) * _num_mapped_dims;
    size_t node_extra_size = 2 * sizeof(Node *); // left/right child ptr
    size_t entry_size = sizeof(Node) + key_extra_size + node_extra_size;
    size_t size = entry_size * _index.size();
    return MemoryUsage(size, size, 0, 0);
}

std::unique_ptr<Value::Index::View>
SimpleValue::create_view(const std::vector<size_t> &dims) const
{
    if (dims.empty()) {
        return std::make_unique<SimpleIterateView>(_index);
    } else if (dims.size() == _num_mapped_dims) {
        return std::make_unique<SimpleLookupView>(_index, _num_mapped_dims);
    } else {
        return std::make_unique<SimpleFilterView>(_index, dims, _num_mapped_dims);
    }
}

std::unique_ptr<Value>
SimpleValue::from_spec(const TensorSpec &spec)
{
    return value_from_spec(spec, SimpleValueBuilderFactory::get());
}

std::unique_ptr<Value>
SimpleValue::from_value(const Value& value)
{
    return from_spec(spec_from_value(value));
}

std::unique_ptr<Value>
SimpleValue::from_stream(nbostream &stream)
{
    return decode_value(stream, SimpleValueBuilderFactory::get());
}

//-----------------------------------------------------------------------------

template <typename T>
SimpleValueT<T>::SimpleValueT(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces_in)
    : SimpleValue(type, num_mapped_dims_in, subspace_size_in),
      _cells()
{
    _cells.reserve(subspace_size_in * expected_subspaces_in);
}

template <typename T>
SimpleValueT<T>::~SimpleValueT() = default;

template <typename T>
ArrayRef<T>
SimpleValueT<T>::add_subspace(ConstArrayRef<vespalib::stringref> addr)
{
    size_t old_size = _cells.size();
    add_mapping(addr);
    _cells.resize(old_size + subspace_size(), std::numeric_limits<T>::quiet_NaN());
    return ArrayRef<T>(&_cells[old_size], subspace_size());
}

//-----------------------------------------------------------------------------

SimpleValueBuilderFactory::SimpleValueBuilderFactory() = default;
SimpleValueBuilderFactory SimpleValueBuilderFactory::_factory;

std::unique_ptr<ValueBuilderBase>
SimpleValueBuilderFactory::create_value_builder_base(const ValueType &type, size_t num_mapped_dims, size_t subspace_size,
                                                     size_t expected_subspaces) const
{
    return typify_invoke<1,TypifyCellType,CreateSimpleValueBuilderBase>(type.cell_type(), type, num_mapped_dims, subspace_size, expected_subspaces);
}

//-----------------------------------------------------------------------------

}
