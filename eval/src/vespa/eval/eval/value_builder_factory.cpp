// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_builder_factory.h"

namespace vespalib::eval {

namespace {

struct CopyValue {
    template <typename CT>
    static std::unique_ptr<Value> invoke(const Value &value,
                                         const ValueType &type,
                                         const ValueBuilderFactory &factory)
    {
        size_t num_mapped = type.count_mapped_dimensions();
        size_t dense_size = type.dense_subspace_size();
        const auto & idx = value.index();
        auto input_cells = value.cells().typify<CT>();
        auto builder = factory.create_value_builder<CT>(type, num_mapped, dense_size, idx.size());
        std::vector<string_id> addr(num_mapped);
        if (num_mapped == 0) {
            assert(idx.size() == 1);
            auto array_ref = builder->add_subspace(addr);
            for (size_t i = 0; i < dense_size; ++i) {
                array_ref[i] = input_cells[i];
            }
        } else {
            auto view = idx.create_view({});
            view->lookup({});
            std::vector<string_id*> addr_fetch;
            addr_fetch.reserve(num_mapped);
            for (auto & label : addr) {
                addr_fetch.push_back(&label);
            }
            size_t subspace_idx;
            while (view->next_result(addr_fetch, subspace_idx)) {
                auto array_ref = builder->add_subspace(addr);
                for (size_t i = 0; i < dense_size; ++i) {
                    array_ref[i] = input_cells[(dense_size * subspace_idx) + i];
                }
            }
        }
        return builder->build(std::move(builder));
    }
};

} // namespace <unnamed>

std::unique_ptr<Value>
ValueBuilderFactory::copy(const Value &value) const
{
    const auto & type = value.type();
    return typify_invoke<1,TypifyCellType,CopyValue>(type.cell_type(),
                                                     value, type, *this);
}

}
