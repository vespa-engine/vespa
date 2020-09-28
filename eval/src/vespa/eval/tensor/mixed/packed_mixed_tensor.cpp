// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packed_mixed_tensor.h"

namespace vespalib::eval::packed_mixed_tensor {

/*********************************************************************************/

class PackedMixedTensorIndexView : public Value::Index::View
{
private:
    const PackedMappings& _mappings;
    const std::vector<size_t> _view_dims;
    std::vector<uint32_t> _lookup_enums;
    std::vector<uint32_t> _full_enums;
    size_t _index;

    size_t num_full_dims() const { return _mappings.num_mapped_dims(); }
    size_t num_view_dims() const { return _view_dims.size(); }
    size_t num_rest_dims() const { return num_full_dims() - num_view_dims(); }
public:
    PackedMixedTensorIndexView(const PackedMappings& mappings,
                               const std::vector<size_t> &dims)
        : _mappings(mappings),
          _view_dims(dims),
          _lookup_enums(),
          _index(0)
    {
        _lookup_enums.reserve(num_view_dims());
        _full_enums.resize(num_full_dims());
    }

    void lookup(const std::vector<const vespalib::stringref*> &addr) override;
    bool next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out) override;
    ~PackedMixedTensorIndexView() override = default;
};

void
PackedMixedTensorIndexView::lookup(const std::vector<const vespalib::stringref*> &addr)
{
    _index = 0;
    assert(addr.size() == num_view_dims());
    _lookup_enums.clear();
    for (const vespalib::stringref * label_ptr : addr) {
        int32_t label_enum = _mappings.label_store().find_label(*label_ptr);
        if (label_enum < 0) {
            // cannot match
            _index = _mappings.size();
            break;
        }
        _lookup_enums.push_back(label_enum);
    }
}

bool
PackedMixedTensorIndexView::next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out)
{
    assert(addr_out.size() == num_rest_dims());
    while (_index < _mappings.size()) {
        idx_out = _mappings.fill_enums_by_sortid(_index++, _full_enums);
        bool couldmatch = true;
        size_t vd_idx = 0;
        size_t ao_idx = 0;
        for (size_t i = 0; i < num_full_dims(); ++i) {
            if (vd_idx < num_view_dims()) {
                size_t next_view_dim = _view_dims[vd_idx];
                if (i == next_view_dim) {
                    if (_lookup_enums[vd_idx] == _full_enums[i]) {
                        // match in this dimension
                        ++vd_idx;
                        continue;
                    } else {
                        // does not match
                        couldmatch = false;
                        break;
                    }
                }
            }
            // not a view dimension:
            uint32_t label_enum = _full_enums[i];
            *addr_out[ao_idx] = _mappings.label_store().get_label(label_enum);
            ++ao_idx;
        }
        if (couldmatch) {
            assert(vd_idx == num_view_dims());
            assert(ao_idx == num_rest_dims());
            return true;
        }
    }
    return false;
}

/*********************************************************************************/

class PackedMixedTensorLookup : public Value::Index::View
{
private:
    const PackedMappings& _mappings;
    std::vector<uint32_t> _lookup_enums;
    bool _first_time;

    size_t num_full_dims() const { return _mappings.num_mapped_dims(); }
public:
    PackedMixedTensorLookup(const PackedMappings& mappings)
        : _mappings(mappings),
          _lookup_enums(),
          _first_time(false)
    {
        _lookup_enums.reserve(num_full_dims());
    }

    void lookup(const std::vector<const vespalib::stringref*> &addr) override;
    bool next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out) override;
    ~PackedMixedTensorLookup() override = default;
};

void
PackedMixedTensorLookup::lookup(const std::vector<const vespalib::stringref*> &addr)
{
    assert(addr.size() == num_full_dims());
    _lookup_enums.clear();
    for (const vespalib::stringref * label_ptr : addr) {
        int32_t label_enum = _mappings.label_store().find_label(*label_ptr);
        if (label_enum < 0) {
            // cannot match
            _first_time = false;
            return;
        }
        _lookup_enums.push_back(label_enum);
    }
    _first_time = true;
}

bool
PackedMixedTensorLookup::next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out)
{
    assert(addr_out.size() == 0);
    if (_first_time) {
        _first_time = false;
        int32_t subspace = _mappings.subspace_of_enums(_lookup_enums);
        if (subspace >= 0) {
            idx_out = subspace;
            return true;
        }
    }
    return false;
}

/*********************************************************************************/

class PackedMixedTensorAllMappings : public Value::Index::View
{
private:
    const PackedMappings& _mappings;
    std::vector<vespalib::stringref> _full_address;
    size_t _index;

public:
    PackedMixedTensorAllMappings(const PackedMappings& mappings)
        : _mappings(mappings),
          _full_address(),
          _index(0)
    {
        _full_address.resize(_mappings.num_mapped_dims());
    }

    void lookup(const std::vector<const vespalib::stringref*> &addr) override;
    bool next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out) override;
    ~PackedMixedTensorAllMappings() override = default;
};

void
PackedMixedTensorAllMappings::lookup(const std::vector<const vespalib::stringref*> &addr)
{
    _index = 0;
    assert(addr.size() == 0);
}

bool
PackedMixedTensorAllMappings::next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out)
{
    assert(addr_out.size() == _mappings.num_mapped_dims());
    while (_index < _mappings.size()) {
        idx_out = _mappings.fill_address_by_sortid(_index++, _full_address);
        for (size_t i = 0; i < _mappings.num_mapped_dims(); ++i) {
            *addr_out[i] = _full_address[i];
        }
        return true;
    }
    return false;
}

/*********************************************************************************/

PackedMixedTensor::~PackedMixedTensor() = default;

std::unique_ptr<Value::Index::View>
PackedMixedTensor::create_view(const std::vector<size_t> &dims) const
{
    if (dims.size() == 0) {
        return std::make_unique<PackedMixedTensorAllMappings>(_mappings);
    }
    for (size_t i = 1; i < dims.size(); ++i) {
        assert(dims[i-1] < dims[i]);
        assert(dims[i] < _mappings.num_mapped_dims());
    }
    if (dims.size() == _mappings.num_mapped_dims()) {
        return std::make_unique<PackedMixedTensorLookup>(_mappings);
    }
    return std::make_unique<PackedMixedTensorIndexView>(_mappings, dims);
}

} // namespace
