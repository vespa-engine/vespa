// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <map>

namespace vespalib {
namespace eval {

/**
 * An implementation-independent specification of the type and
 * contents of a tensor.
 **/
class TensorSpec
{
public:
    struct Label {
        size_t index;
        vespalib::string name;
        static constexpr size_t npos = -1;
        Label(size_t index_in) : index(index_in), name() {}
        Label(const vespalib::string &name_in) : index(npos), name(name_in) {}
        Label(const char *name_in) : index(npos), name(name_in) {}
        bool is_mapped() const { return (index == npos); }
        bool is_indexed() const { return (index != npos); }
    };
    using Address = std::map<vespalib::string,Label>;
    using Cell = std::pair<Address,double>;
private:
    vespalib::string _type;
    std::vector<Cell> _cells;
public:
    TensorSpec(const vespalib::string &type_spec) : _type(type_spec), _cells() {}
    TensorSpec &add(const Address &address, double value) {
        _cells.emplace_back(address, value);
        return *this;
    }
    const vespalib::string &type() const { return _type; }
    const std::vector<Cell> &cells() const { return _cells; }
};

} // namespace vespalib::eval
} // namespace vespalib
