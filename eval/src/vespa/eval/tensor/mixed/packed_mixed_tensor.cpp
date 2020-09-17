// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packed_mixed_tensor.h"

namespace vespalib::eval {

class PackedMixedTensorIndexView : public NewValue::Index::View
{
private:
    const PackedMappings& _mappings;
    const std::vector<size_t> _view_dims;
    std::vector<uint32_t> _lookup_enums;
    std::vector<uint32_t> _full_enums;
    std::vector<vespalib::stringref> _lookup_addr;
    std::vector<vespalib::stringref> _full_address;
    size_t _index;

    size_t num_full_dims() const { return _mappings.num_sparse_dims(); }
    size_t num_view_dims() const { return _view_dims.size(); }
    size_t num_rest_dims() const { return num_full_dims() - num_view_dims(); }
public:
    PackedMixedTensorIndexView(const PackedMappings& mappings,
                               const std::vector<size_t> &dims)
        : _mappings(mappings),
          _view_dims(dims),
          _lookup_enums(),
          _lookup_addr(),
          _full_address(),
          _index(0)
    {
        _lookup_enums.reserve(num_view_dims());
        _lookup_addr.reserve(num_view_dims());
        _full_enums.resize(num_full_dims());
        _full_address.resize(num_full_dims());
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
    _lookup_addr.clear();
 // printf("lookup %zu/%zu dims:", num_view_dims(), num_full_dims());
    for (const vespalib::stringref * label_ptr : addr) {
        int32_t label_enum = _mappings.label_store().find_label(*label_ptr);
        if (label_enum < 0) {
            // cannot match
            _index = _mappings.size();
            break;
        }
        _lookup_enums.push_back(label_enum);
        _lookup_addr.push_back(*label_ptr);
 //     printf(" '%s'", label_ptr->data());
    }
 // printf(" [in %u mappings]\n", _mappings.size());
}

bool
PackedMixedTensorIndexView::next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out)
{
    assert(addr_out.size() == num_rest_dims());
    while (_index < _mappings.size()) {
        idx_out = _mappings.fill_by_subspace(_index++, _full_enums);
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
            auto label_value = _mappings.label_store().label_value(label_enum);
            _full_address[i] = label_value;
            *addr_out[ao_idx] = label_value;
            ++ao_idx;
        }
        if (couldmatch) {
 //         printf("matches at %zu/%u\n", _index, _mappings.size());
            assert(vd_idx == num_view_dims());
            assert(ao_idx == num_rest_dims());
            return true;
        }
    }
 // printf("no more matches %zu/%u\n", _index, _mappings.size());
    return false;
}

PackedMixedTensor::~PackedMixedTensor() = default;

std::unique_ptr<NewValue::Index::View>
PackedMixedTensor::create_view(const std::vector<size_t> &dims) const
{
    for (size_t i = 1; i < dims.size(); ++i) {
        assert(dims[i-1] < dims[i]);
        assert(dims[i] < _mappings.num_sparse_dims());
    }
    return std::make_unique<PackedMixedTensorIndexView>(_mappings, dims);
}

} // namespace
