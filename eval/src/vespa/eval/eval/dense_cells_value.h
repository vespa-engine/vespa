// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value.h>
#include <cassert>

namespace vespalib::eval {

/**
 * A dense-only value that just owns a vector of cells.
 **/
template<typename T>
class DenseCellsValue : public Value {
private:
    ValueType _type;
    std::vector<T> _cells;
public:
    DenseCellsValue(const ValueType &type_ref, std::vector<T> cells)
      : _type(type_ref), _cells(std::move(cells))
    {
        assert(check_cell_type<T>(_type.cell_type()));
        assert(_cells.size() == _type.dense_subspace_size());
    }
    const ValueType &type() const override { return _type; }
    TypedCells cells() const override { return TypedCells(_cells); }
    const Index &index() const override { return TrivialIndex::get(); }
    MemoryUsage get_memory_usage() const override;
    ~DenseCellsValue();
};

}
