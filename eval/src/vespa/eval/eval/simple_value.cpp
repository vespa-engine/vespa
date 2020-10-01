// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_value.h"
#include "inline_operation.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <vespa/vespalib/util/overload.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.simple_value");

namespace vespalib::eval {

//-----------------------------------------------------------------------------

namespace {

struct CreateSimpleValueBuilderBase {
    template <typename T> static std::unique_ptr<ValueBuilderBase> invoke(const ValueType &type,
            size_t num_mapped_dims_in, size_t subspace_size_in)
    {
        assert(check_cell_type<T>(type.cell_type()));
        return std::make_unique<SimpleValueT<T>>(type, num_mapped_dims_in, subspace_size_in);
    }
};

class SimpleValueView : public Value::Index::View {
private:
    using Addr = std::vector<vespalib::string>;
    using Map = std::map<Addr,size_t>;
    using Itr = Map::const_iterator;

    const Map          &_index;
    size_t              _num_mapped_dims;
    std::vector<size_t> _match_dims;
    std::vector<size_t> _extract_dims;
    Addr                _query;
    Itr                 _pos;

    bool is_direct_lookup() const { return (_match_dims.size() == _num_mapped_dims); }
    bool is_match() const {
        assert(_pos->first.size() == _num_mapped_dims);
        for (size_t idx: _match_dims) {
            if (_query[idx] != _pos->first[idx]) {
                return false;
            }
        }
        return true;
    }

public:
    SimpleValueView(const Map &index, const std::vector<size_t> &match_dims, size_t num_mapped_dims)
        : _index(index), _num_mapped_dims(num_mapped_dims), _match_dims(match_dims), _extract_dims(), _query(num_mapped_dims, ""), _pos(_index.end())
    {
        auto pos = _match_dims.begin();
        for (size_t i = 0; i < _num_mapped_dims; ++i) {
            if ((pos == _match_dims.end()) || (*pos != i)) {
                _extract_dims.push_back(i);
            } else {
                ++pos;
            }
        }
        assert(pos == _match_dims.end());
        assert((_match_dims.size() + _extract_dims.size()) == _num_mapped_dims);
    }

    void lookup(ConstArrayRef<const vespalib::stringref*> addr) override {
        assert(addr.size() == _match_dims.size());
        for (size_t i = 0; i < _match_dims.size(); ++i) {
            _query[_match_dims[i]] = *addr[i];
        }
        if (is_direct_lookup()) {
            _pos = _index.find(_query);
        } else {
            _pos = _index.begin();
        }
    }

    bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) override {
        assert(addr_out.size() == _extract_dims.size());
        while (_pos != _index.end()) {
            if (is_match()) {
                for (size_t i = 0; i < _extract_dims.size(); ++i) {
                    *addr_out[i] = _pos->first[_extract_dims[i]];
                }
                idx_out = _pos->second;
                if (is_direct_lookup()) {
                    _pos = _index.end();
                } else {
                    ++_pos;
                }
                return true;
            }
            ++_pos;
        }
        return false;
    }
};

} // namespace <unnamed>

//-----------------------------------------------------------------------------

void
SimpleValue::add_mapping(ConstArrayRef<vespalib::stringref> addr)
{
    size_t id = _index.size();
    std::vector<vespalib::string> my_addr;
    for (const auto &label: addr) {
        my_addr.push_back(label);
    }
    auto res = _index.emplace(std::move(my_addr), id);
    assert(res.second);
}

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

std::unique_ptr<Value::Index::View>
SimpleValue::create_view(const std::vector<size_t> &dims) const
{
    return std::make_unique<SimpleValueView>(_index, dims, _num_mapped_dims);
}

//-----------------------------------------------------------------------------

template <typename T>
SimpleValueT<T>::SimpleValueT(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in)
    : SimpleValue(type, num_mapped_dims_in, subspace_size_in),
      _cells()
{
}

template <typename T>
SimpleValueT<T>::~SimpleValueT() = default;

template <typename T>
ArrayRef<T>
SimpleValueT<T>::add_subspace(ConstArrayRef<vespalib::stringref> addr)
{
    size_t old_size = _cells.size();
    assert(old_size == (index().size() * subspace_size()));
    add_mapping(addr);
    _cells.resize(old_size + subspace_size());
    return ArrayRef<T>(&_cells[old_size], subspace_size());
}

//-----------------------------------------------------------------------------

SimpleValueBuilderFactory::SimpleValueBuilderFactory() = default;
SimpleValueBuilderFactory SimpleValueBuilderFactory::_factory;

std::unique_ptr<ValueBuilderBase>
SimpleValueBuilderFactory::create_value_builder_base(const ValueType &type,
                                                     size_t num_mapped_dims_in, size_t subspace_size_in, size_t) const
{
    return typify_invoke<1,TypifyCellType,CreateSimpleValueBuilderBase>(type.cell_type(), type, num_mapped_dims_in, subspace_size_in);
}

//-----------------------------------------------------------------------------

}
