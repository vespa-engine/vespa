// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_value.h"
#include "inline_operation.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <vespa/vespalib/util/overload.h>
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
struct LookupView : public Value::Index::View {

    const SimpleSparseMap &map;
    size_t                 subspace;

    LookupView(const SimpleSparseMap &map_in)
        : map(map_in), subspace(SimpleSparseMap::npos()) {}

    void lookup(ConstArrayRef<const vespalib::stringref*> addr) override {
        subspace = map.lookup(addr);
    }

    bool next_result(ConstArrayRef<vespalib::stringref*>, size_t &idx_out) override {
        if (subspace == SimpleSparseMap::npos()) {
            return false;
        }
        idx_out = subspace;
        subspace = SimpleSparseMap::npos();
        return true;
    }
};

//-----------------------------------------------------------------------------

// find matching mappings for a partial address with brute force filtering
struct FilterView : public Value::Index::View {

    using Label = SimpleSparseMap::HashedLabel;

    size_t                    num_mapped_dims;
    const std::vector<Label> &labels;
    std::vector<size_t>       match_dims;
    std::vector<size_t>       extract_dims;
    std::vector<Label>        query;
    size_t                    pos;

    bool is_match() const {
        for (size_t i = 0; i < query.size(); ++i) {
            if (query[i].hash != labels[pos + match_dims[i]].hash) {
                return false;
            }
        }
        return true;
    }

    FilterView(const SimpleSparseMap &map, const std::vector<size_t> &match_dims_in)
        : num_mapped_dims(map.num_dims()), labels(map.labels()), match_dims(match_dims_in),
          extract_dims(), query(match_dims.size(), Label()), pos(labels.size())
    {
        auto my_pos = match_dims.begin();
        for (size_t i = 0; i < num_mapped_dims; ++i) {
            if ((my_pos == match_dims.end()) || (*my_pos != i)) {
                extract_dims.push_back(i);
            } else {
                ++my_pos;
            }
        }
        assert(my_pos == match_dims.end());
        assert((match_dims.size() + extract_dims.size()) == num_mapped_dims);
    }

    void lookup(ConstArrayRef<const vespalib::stringref*> addr) override {
        assert(addr.size() == query.size());
        for (size_t i = 0; i < addr.size(); ++i) {
            query[i] = Label(*addr[i]);
        }
        pos = 0;
    }

    bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) override {
        while (pos < labels.size()) {
            if (is_match()) {
                assert(addr_out.size() == extract_dims.size());
                for (size_t i = 0; i < extract_dims.size(); ++i) {
                    *addr_out[i] = labels[pos + extract_dims[i]].label;
                }
                idx_out = (pos / num_mapped_dims); // is this expensive?
                pos += num_mapped_dims;
                return true;
            }
            pos += num_mapped_dims;
        }
        return false;
    }
};

//-----------------------------------------------------------------------------

// iterate all mappings
struct IterateView : public Value::Index::View {

    using Labels = std::vector<SimpleSparseMap::HashedLabel>;

    size_t        num_mapped_dims;
    const Labels &labels;
    size_t        pos;

    IterateView(const SimpleSparseMap &map)
        : num_mapped_dims(map.num_dims()), labels(map.labels()), pos(labels.size()) {}

    void lookup(ConstArrayRef<const vespalib::stringref*>) override {
        pos = 0;
    }

    bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) override {
        if (pos >= labels.size()) {
            return false;
        }
        assert(addr_out.size() == num_mapped_dims);
        for (size_t i = 0; i < num_mapped_dims; ++i) {
            *addr_out[i] = labels[pos + i].label;
        }
        idx_out = (pos / num_mapped_dims); // is this expensive?
        pos += num_mapped_dims;
        return true;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

//-----------------------------------------------------------------------------

std::unique_ptr<Value::Index::View>
SimpleValueIndex::create_view(const std::vector<size_t> &dims) const
{
    if (map.num_dims() == 0) {
        return TrivialIndex::get().create_view(dims);
    } else if (dims.empty()) {
        return std::make_unique<IterateView>(map);
    } else if (dims.size() == map.num_dims()) {
        return std::make_unique<LookupView>(map);
    } else {
        return std::make_unique<FilterView>(map, dims);
    }
}

//-----------------------------------------------------------------------------

SimpleValue::SimpleValue(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces_in)
    : _type(type),
      _subspace_size(subspace_size_in),
      _index(num_mapped_dims_in, expected_subspaces_in)
{
    assert(_type.count_mapped_dimensions() == _index.map.num_dims());
    assert(_type.dense_subspace_size() == _subspace_size);
}

SimpleValue::~SimpleValue() = default;

//-----------------------------------------------------------------------------

template <typename T>
SimpleValueT<T>::SimpleValueT(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces_in)
    : SimpleValue(type, num_mapped_dims_in, subspace_size_in, expected_subspaces_in),
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
    _cells.resize(old_size + subspace_size());
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
