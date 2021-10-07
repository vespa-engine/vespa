// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <ostream>

namespace storage {

template <typename T>
class VectorPrinter
{
public:
    VectorPrinter(const std::vector<T>& vec,
                  const char* separator)
        : _vec(&vec),
          _separator(separator)
    {}

    void print(std::ostream& os) const {
        for (uint32_t i = 0; i < _vec->size(); ++i) {
            if (i != 0) {
                os << _separator;
            }
            os << (*_vec)[i];
        }
    }
private:
    const std::vector<T>* _vec;
    const char* _separator;
};

template <typename T>
inline std::ostream&
operator<<(std::ostream& os, const VectorPrinter<T>& printer)
{
    printer.print(os);
    return os;
}

template <typename T>
inline VectorPrinter<T>
commaSeparated(const std::vector<T>& vec)
{
    return VectorPrinter<T>(vec, ",");
}

}

