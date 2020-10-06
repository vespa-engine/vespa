// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/string.h>
#include <iosfwd>
#include <map>
#include <vector>

namespace vespalib::tensor {

/**
 * A sparse immutable address to a tensor cell.
 *
 * Only dimensions which have a different label than "undefined" are explicitly included.
 * Tensor addresses are ordered by increasing size primarily,
 * and by the natural order of the elements in sorted order secondarily.
 */
class TensorAddress
{
public:
    typedef std::unique_ptr<TensorAddress> UP;

    class Element
    {
    private:
        vespalib::string _dimension;
        vespalib::string _label;

    public:
        static const vespalib::string UNDEFINED_LABEL;
        Element(const vespalib::string &dimension_in, const vespalib::string &label_in) noexcept
            : _dimension(dimension_in), _label(label_in)
        {}
        Element(const Element &) noexcept = default;
        Element & operator = (const Element &) noexcept = default;
        Element(Element &&) noexcept = default;
        Element & operator = (Element &&) noexcept = default;
        ~Element();
        const vespalib::string &dimension() const { return _dimension; }
        const vespalib::string &label() const { return _label; }
        bool operator<(const Element &rhs) const {
            if (_dimension == rhs._dimension) {
                // Define sort order when dimension is the same to be able
                // to do set operations over element vectors.
                return _label < rhs._label;
            }
            return _dimension < rhs._dimension;
        }
        bool operator==(const Element &rhs) const {
            return (_dimension == rhs._dimension) && (_label == rhs._label);
        }
        bool operator!=(const Element &rhs) const {
            return !(*this == rhs);
        }
        size_t hash() const {
            return hashValue(_dimension.c_str()) + hashValue(_label.c_str());
        }
    };

    typedef std::vector<Element> Elements;

private:
    Elements _elements;

public:
    TensorAddress();
    explicit TensorAddress(const Elements &elements_in);
    explicit TensorAddress(Elements &&elements_in)
        : _elements(std::move(elements_in))
    {}
    TensorAddress(const TensorAddress &) = default;
    TensorAddress & operator = (const TensorAddress &) = default;
    TensorAddress(TensorAddress &&) = default;
    TensorAddress & operator = (TensorAddress &&) = default;

    ~TensorAddress();
    const Elements &elements() const { return _elements; }
    bool hasDimension(const vespalib::string &dimension) const;
    bool operator<(const TensorAddress &rhs) const;
    bool operator==(const TensorAddress &rhs) const;
    size_t hash() const;
};

std::ostream &operator<<(std::ostream &out, const TensorAddress::Elements &elements);
std::ostream &operator<<(std::ostream &out, const TensorAddress &value);

}
