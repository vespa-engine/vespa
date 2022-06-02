// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_value.h"
#include <vespa/vespalib/util/typify.h>
#include "fast_value.hpp"

namespace vespalib::eval {

//-----------------------------------------------------------------------------

namespace {

struct CreateFastValueBuilderBase {
    template <typename T, typename R2> static std::unique_ptr<ValueBuilderBase> invoke(const ValueType &type,
            size_t num_mapped_dims, size_t subspace_size, size_t expected_subspaces)
    {
        assert(check_cell_type<T>(type.cell_type()));
        if (type.is_double()) {
            return std::make_unique<FastDoubleValueBuilder>();
        } else if (num_mapped_dims == 0) {
            return std::make_unique<FastDenseValue<T>>(type, subspace_size);
        } else {
            return std::make_unique<FastValue<T,R2::value>>(type, num_mapped_dims, subspace_size, expected_subspaces);
        }
    }
};

//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------

// look up a full address in the map directly
struct FastLookupView : public Value::Index::View {

    const FastAddrMap &map;
    size_t             subspace;

    FastLookupView(const FastAddrMap &map_in)
            : map(map_in), subspace(FastAddrMap::npos()) {}

    void lookup(ConstArrayRef<const string_id*> addr) override {
        subspace = map.lookup(addr);
    }

    bool next_result(ConstArrayRef<string_id*>, size_t &idx_out) override {
        if (subspace == FastAddrMap::npos()) {
            return false;
        }
        idx_out = subspace;
        subspace = FastAddrMap::npos();
        return true;
    }
};

//-----------------------------------------------------------------------------

// iterate all mappings
struct FastIterateView : public Value::Index::View {

    const FastAddrMap &map;
    size_t             pos;

    FastIterateView(const FastAddrMap &map_in)
            : map(map_in), pos(FastAddrMap::npos()) {}

    void lookup(ConstArrayRef<const string_id*>) override {
        pos = 0;
    }

    bool next_result(ConstArrayRef<string_id*> addr_out, size_t &idx_out) override {
        if (pos >= map.size()) {
            return false;
        }
        auto addr = map.get_addr(pos);
        assert(addr.size() == addr_out.size());
        for (size_t i = 0; i < addr.size(); ++i) {
            *addr_out[i] = addr[i];
        }
        idx_out = pos++;
        return true;
    }
};
// find matching mappings for a partial address with brute force filtering
struct FastFilterView : public Value::Index::View {

    const FastAddrMap        &map;
    SmallVector<size_t>       match_dims;
    SmallVector<size_t>       extract_dims;
    SmallVector<string_id>    query;
    size_t                    pos;

    bool is_match(ConstArrayRef<string_id> addr) const {
        for (size_t i = 0; i < query.size(); ++i) {
            if (query[i] != addr[match_dims[i]]) {
                return false;
            }
        }
        return true;
    }

    FastFilterView(const FastAddrMap &map_in, ConstArrayRef<size_t> match_dims_in) __attribute__((noinline));
    FastFilterView(const FastFilterView &) = delete;
    FastFilterView & operator =(const FastFilterView &) = delete;
    ~FastFilterView() override;

    void lookup(ConstArrayRef<const string_id*> addr) override {
        assert(addr.size() == query.size());
        for (size_t i = 0; i < addr.size(); ++i) {
            query[i] = *addr[i];
        }
        pos = 0;
    }

    bool next_result(ConstArrayRef<string_id*> addr_out, size_t &idx_out) override {
        while (pos < map.size()) {
            auto addr = map.get_addr(pos);
            if (is_match(addr)) {
                assert(addr_out.size() == extract_dims.size());
                for (size_t i = 0; i < extract_dims.size(); ++i) {
                    *addr_out[i] = addr[extract_dims[i]];
                }
                idx_out = pos++;
                return true;
            }
            ++pos;
        }
        return false;
    }
};

FastFilterView::FastFilterView(const FastAddrMap &map_in, ConstArrayRef<size_t> match_dims_in)
    : map(map_in), match_dims(match_dims_in.begin(), match_dims_in.end()),
      extract_dims(), query(match_dims.size()), pos(FastAddrMap::npos())
{
    auto my_pos = match_dims.begin();
    for (size_t i = 0; i < map.addr_size(); ++i) {
        if ((my_pos == match_dims.end()) || (*my_pos != i)) {
            extract_dims.push_back(i);
        } else {
            ++my_pos;
        }
    }
    assert(my_pos == match_dims.end());
    assert((match_dims.size() + extract_dims.size()) == map.addr_size());
}

FastFilterView::~FastFilterView() = default;
} // namespace <unnamed>

//-----------------------------------------------------------------------------

std::unique_ptr<Value::Index::View>
FastValueIndex::create_view(ConstArrayRef<size_t> dims) const
{
    if (map.addr_size() == 0) {
        return TrivialIndex::get().create_view(dims);
    } else if (dims.empty()) {
        return std::make_unique<FastIterateView>(map);
    } else if (dims.size() == map.addr_size()) {
        return std::make_unique<FastLookupView>(map);
    } else {
        return std::make_unique<FastFilterView>(map, dims);
    }
}

//-----------------------------------------------------------------------------

FastValueBuilderFactory::FastValueBuilderFactory() = default;
FastValueBuilderFactory FastValueBuilderFactory::_factory;

std::unique_ptr<ValueBuilderBase>
FastValueBuilderFactory::create_value_builder_base(const ValueType &type, bool transient, size_t num_mapped_dims, size_t subspace_size,
                                                   size_t expected_subspaces) const
{
    using MyTypify = TypifyValue<TypifyCellType,TypifyBool>;
    return typify_invoke<2,MyTypify,CreateFastValueBuilderBase>(type.cell_type(), transient, type, num_mapped_dims, subspace_size, expected_subspaces);
}

//-----------------------------------------------------------------------------

}
