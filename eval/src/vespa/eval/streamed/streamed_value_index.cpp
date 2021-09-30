// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "streamed_value_index.h"
#include "streamed_value_utils.h"

#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.streamed_value_index");

namespace vespalib::eval {

namespace {

struct StreamedFilterView : Value::Index::View
{
    LabelBlockStream label_blocks;
    std::vector<size_t> view_dims;
    std::vector<string_id> to_match;

    StreamedFilterView(LabelBlockStream labels, ConstArrayRef<size_t> view_dims_in)
      : label_blocks(std::move(labels)),
        view_dims(view_dims_in.begin(), view_dims_in.end()),
        to_match()
    {
        to_match.reserve(view_dims.size());
    }

    void lookup(ConstArrayRef<const string_id*> addr) override {
        label_blocks.reset();
        to_match.clear();
        for (auto ptr : addr) {
            to_match.push_back(*ptr);
        }
        assert(view_dims.size() == to_match.size());
    }

    bool next_result(ConstArrayRef<string_id*> addr_out, size_t &idx_out) override {
        while (const auto block = label_blocks.next_block()) {
            idx_out = block.subspace_index;
            bool matches = true;
            size_t out_idx = 0;
            size_t vdm_idx = 0;
            for (size_t dim = 0; dim < block.address.size(); ++dim) {
                if (vdm_idx < view_dims.size() && (view_dims[vdm_idx] == dim)) {
                    matches &= (block.address[dim] == to_match[vdm_idx++]);
                } else {
                    *addr_out[out_idx++] = block.address[dim];
                }
            }
            assert(out_idx == addr_out.size());
            assert(vdm_idx == view_dims.size());
            if (matches) return true;
        }
        return false;
    }
};

struct StreamedIterationView : Value::Index::View
{
    LabelBlockStream label_blocks;

    StreamedIterationView(LabelBlockStream labels)
      : label_blocks(std::move(labels))
    {}

    void lookup(ConstArrayRef<const string_id*> addr) override {
        label_blocks.reset();
        assert(addr.size() == 0);
    }

    bool next_result(ConstArrayRef<string_id*> addr_out, size_t &idx_out) override {
        if (auto block = label_blocks.next_block()) {
            idx_out = block.subspace_index;
            size_t i = 0;
            assert(addr_out.size() == block.address.size());
            for (auto ptr : addr_out) {
                *ptr = block.address[i++];
            }
            return true;
        }
        return false;
    }
};

} // namespace <unnamed>

std::unique_ptr<Value::Index::View>
StreamedValueIndex::create_view(ConstArrayRef<size_t> dims) const
{
    LabelBlockStream label_stream(_num_subspaces, _labels_ref, _num_mapped_dims);
    if (dims.empty()) {
        return std::make_unique<StreamedIterationView>(std::move(label_stream));
    }
    return std::make_unique<StreamedFilterView>(std::move(label_stream), dims);
}

} // namespace vespalib::eval
