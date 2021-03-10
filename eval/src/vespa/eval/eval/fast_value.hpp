// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include "fast_addr_map.h"
#include "inline_operation.h"
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <typeindex>

namespace vespalib::eval {

//-----------------------------------------------------------------------------

namespace {

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

    FastFilterView(const FastAddrMap &map_in, ConstArrayRef<size_t> match_dims_in)
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

//-----------------------------------------------------------------------------

} // namespace <unnamed>

//-----------------------------------------------------------------------------

// This is the class instructions will look for when optimizing sparse
// operations by calling inline functions directly.
struct FastValueIndex final : Value::Index {
    FastAddrMap map;
    FastValueIndex(size_t num_mapped_dims_in, const std::vector<string_id> &labels, size_t expected_subspaces_in)
        : map(num_mapped_dims_in, labels, expected_subspaces_in) {}
    size_t size() const override { return map.size(); }
    std::unique_ptr<View> create_view(ConstArrayRef<size_t> dims) const override;
};

inline bool is_fast(const Value::Index &index) {
    return (std::type_index(typeid(index)) == std::type_index(typeid(FastValueIndex)));
}

inline bool are_fast(const Value::Index &a, const Value::Index &b) {
    return (is_fast(a) && is_fast(b));
}

constexpr const FastValueIndex &as_fast(const Value::Index &index) {
    return static_cast<const FastValueIndex &>(index);
}

//-----------------------------------------------------------------------------

template <typename T>
struct FastCells {
    static constexpr size_t elem_size = sizeof(T);
    size_t capacity;
    size_t size;
    void *memory;
    FastCells(size_t initial_capacity)
        : capacity(roundUp2inN(initial_capacity)),
          size(0),
          memory(malloc(elem_size * capacity))
    {
        static_assert(std::is_trivially_copyable_v<T>);
        static_assert(can_skip_destruction<T>::value);
    }
    ~FastCells() {
        free(memory);
    }
    void ensure_free(size_t need) {
        if (__builtin_expect((size + need) > capacity, false)) {
            capacity = roundUp2inN(size + need);
            void *new_memory = malloc(elem_size * capacity);
            memcpy(new_memory, memory, elem_size * size);
            free(memory);
            memory = new_memory;
        }
    }
    constexpr T *get(size_t offset) const {
        return reinterpret_cast<T*>(memory) + offset;
    }
    void push_back_fast(T value) {
        *get(size++) = value;
    }
    ArrayRef<T> add_cells(size_t n) {
        size_t old_size = size;
        ensure_free(n);
        size += n;
        return ArrayRef<T>(get(old_size), n);
    }
    MemoryUsage estimate_extra_memory_usage() const {
        MemoryUsage usage;
        usage.incAllocatedBytes(elem_size * capacity);
        usage.incUsedBytes(elem_size * size);
        return usage;
    }
};

//-----------------------------------------------------------------------------

template <typename T, bool transient>
struct FastValue final : Value, ValueBuilder<T> {

    using Handles = typename std::conditional<transient,
                                     std::vector<string_id>,
                                     SharedStringRepo::Handles>::type;

    static const std::vector<string_id> &get_view(const std::vector<string_id> &handles) { return handles; }
    static const std::vector<string_id> &get_view(const SharedStringRepo::Handles &handles) { return handles.view(); }

    ValueType my_type;
    size_t my_subspace_size;
    Handles my_handles;
    FastValueIndex my_index;
    FastCells<T> my_cells;

    FastValue(const ValueType &type_in, size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces_in)
        : my_type(type_in), my_subspace_size(subspace_size_in),
          my_handles(),
          my_index(num_mapped_dims_in, get_view(my_handles), expected_subspaces_in),
          my_cells(subspace_size_in * expected_subspaces_in)
    {
        my_handles.reserve(expected_subspaces_in * num_mapped_dims_in);
    }
    ~FastValue() override;
    const ValueType &type() const override { return my_type; }
    const Value::Index &index() const override { return my_index; }
    TypedCells cells() const override { return TypedCells(my_cells.memory, get_cell_type<T>(), my_cells.size); }
    void add_mapping(ConstArrayRef<vespalib::stringref> addr) {
        if constexpr (transient) {
            (void) addr;
            abort(); // cannot use this for transient values
        } else {
            uint32_t hash = 0;
            for (const auto &label: addr) {
                hash = FastAddrMap::combine_label_hash(hash, FastAddrMap::hash_label(my_handles.add(label)));
            }
            my_index.map.add_mapping(hash);
        }
    }
    void add_mapping(ConstArrayRef<string_id> addr) {
        uint32_t hash = 0;
        for (string_id label: addr) {
            hash = FastAddrMap::combine_label_hash(hash, FastAddrMap::hash_label(label));
            my_handles.push_back(label);
        }
        my_index.map.add_mapping(hash);
    }
    void add_mapping(ConstArrayRef<string_id> addr, uint32_t hash) {
        for (string_id label: addr) {
            my_handles.push_back(label);
        }
        my_index.map.add_mapping(hash);
    }
    void add_singledim_mapping(string_id label) {
        my_handles.push_back(label);
        my_index.map.add_mapping(FastAddrMap::hash_label(label));
    }
    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref> addr) override {
        add_mapping(addr);
        return my_cells.add_cells(my_subspace_size);
    }
    ArrayRef<T> add_subspace(ConstArrayRef<string_id> addr) override {
        add_mapping(addr);
        return my_cells.add_cells(my_subspace_size);        
    }
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>> self) override {
        if (my_index.map.addr_size() == 0) {
            assert(my_index.map.size() == 1);
        }
        assert(my_cells.size == (my_index.map.size() * my_subspace_size));
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<Value>(this);
    }
    MemoryUsage get_memory_usage() const override {
        MemoryUsage usage = self_memory_usage<FastValue<T,transient>>();
        usage.merge(vector_extra_memory_usage(get_view(my_handles)));
        usage.merge(my_index.map.estimate_extra_memory_usage());
        usage.merge(my_cells.estimate_extra_memory_usage());
        return usage;
    }
};
template <typename T,bool transient> FastValue<T,transient>::~FastValue() = default;

//-----------------------------------------------------------------------------

template <typename T>
struct FastDenseValue final : Value, ValueBuilder<T> {

    ValueType my_type;
    FastCells<T> my_cells;

    FastDenseValue(const ValueType &type_in, size_t subspace_size_in)
        : my_type(type_in), my_cells(subspace_size_in)
    {
        my_cells.add_cells(subspace_size_in);
    }
    ~FastDenseValue() override;
    const ValueType &type() const override { return my_type; }
    const Value::Index &index() const override { return TrivialIndex::get(); }
    TypedCells cells() const override { return TypedCells(my_cells.memory, get_cell_type<T>(), my_cells.size); }
    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref>) override {
        return ArrayRef<T>(my_cells.get(0), my_cells.size);
    }
    ArrayRef<T> add_subspace(ConstArrayRef<string_id>) override {
        return ArrayRef<T>(my_cells.get(0), my_cells.size);
    }
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>> self) override {
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<Value>(this);
    }
    MemoryUsage get_memory_usage() const override {
        MemoryUsage usage = self_memory_usage<FastDenseValue<T>>();
        usage.merge(my_cells.estimate_extra_memory_usage());
        return usage;
    }
};
template <typename T> FastDenseValue<T>::~FastDenseValue() = default;

//-----------------------------------------------------------------------------

struct FastDoubleValueBuilder final : ValueBuilder<double> {
    double _value;
    ArrayRef<double> add_subspace(ConstArrayRef<vespalib::stringref>) final override { return ArrayRef<double>(&_value, 1); }
    ArrayRef<double> add_subspace(ConstArrayRef<string_id>) final override { return ArrayRef<double>(&_value, 1); };
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<double>>) final override { return std::make_unique<DoubleValue>(_value); }
};

//-----------------------------------------------------------------------------

}
