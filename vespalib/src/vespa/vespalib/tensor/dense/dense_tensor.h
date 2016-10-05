// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/types.h>

namespace vespalib {
namespace tensor {

/**
 * A dense tensor where all dimensions are indexed.
 * Tensor cells are stored in an underlying array according to the order of the dimensions.
 */
class DenseTensor : public Tensor
{
public:
    typedef std::unique_ptr<DenseTensor> UP;
    using Cells = std::vector<double>;

    class DimensionMeta
    {
        vespalib::string _dimension;
        size_t _size;

    public:
        DimensionMeta(const vespalib::string & dimension_in, size_t size_in)
            : _dimension(dimension_in),
              _size(size_in)
        {
        }

        const vespalib::string &dimension() const { return _dimension; }
        size_t size() const { return _size; }

        bool operator==(const DimensionMeta &rhs) const {
            return (_dimension == rhs._dimension) &&
                    (_size == rhs._size);
        }
        bool operator!=(const DimensionMeta &rhs) const {
            return !(*this == rhs);
        }
        bool operator<(const DimensionMeta &rhs) const {
            if (_dimension == rhs._dimension) {
                return _size < rhs._size;
            }
            return _dimension < rhs._dimension;
        }
    };

    using DimensionsMeta = std::vector<DimensionMeta>;

    class CellsIterator
    {
    private:
        const DimensionsMeta &_dimensionsMeta;
        const Cells &_cells;
        size_t _cellIdx;
        std::vector<size_t> _address;

    public:
        CellsIterator(const DimensionsMeta &dimensionsMeta,
                      const Cells &cells)
            : _dimensionsMeta(dimensionsMeta),
              _cells(cells),
              _cellIdx(0),
              _address(dimensionsMeta.size(), 0)
        {}
        bool valid() const { return _cellIdx < _cells.size(); }
        void next();
        double cell() const { return _cells[_cellIdx]; }
        const std::vector<size_t> &address() const { return _address; }
        const DimensionsMeta &dimensions() const { return _dimensionsMeta; }
    };


private:
    DimensionsMeta _dimensionsMeta;
    Cells _cells;

public:
    DenseTensor();
    DenseTensor(const DimensionsMeta &dimensionsMeta_in,
                const Cells &cells_in);
    DenseTensor(const DimensionsMeta &dimensionsMeta_in,
                Cells &&cells_in);
    DenseTensor(DimensionsMeta &&dimensionsMeta_in,
                Cells &&cells_in);
    const DimensionsMeta &dimensionsMeta() const { return _dimensionsMeta; }
    const Cells &cells() const { return _cells; }
    bool operator==(const DenseTensor &rhs) const;
    CellsIterator cellsIterator() const { return CellsIterator(_dimensionsMeta, _cells); }

    virtual eval::ValueType getType() const override;
    virtual double sum() const override;
    virtual Tensor::UP add(const Tensor &arg) const override;
    virtual Tensor::UP subtract(const Tensor &arg) const override;
    virtual Tensor::UP multiply(const Tensor &arg) const override;
    virtual Tensor::UP min(const Tensor &arg) const override;
    virtual Tensor::UP max(const Tensor &arg) const override;
    virtual Tensor::UP match(const Tensor &arg) const override;
    virtual Tensor::UP apply(const CellFunction &func) const override;
    virtual Tensor::UP sum(const vespalib::string &dimension) const override;
    virtual bool equals(const Tensor &arg) const override;
    virtual void print(std::ostream &out) const override;
    virtual vespalib::string toString() const override;
    virtual Tensor::UP clone() const override;
    virtual eval::TensorSpec toSpec() const override;
    virtual void accept(TensorVisitor &visitor) const override;
};

std::ostream &operator<<(std::ostream &out, const DenseTensor::DimensionMeta &value);

} // namespace vespalib::tensor
} // namespace vespalib
