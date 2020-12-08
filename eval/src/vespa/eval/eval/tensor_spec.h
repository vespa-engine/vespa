// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/approx.h>
#include <memory>
#include <map>

namespace vespalib {

namespace slime {

struct Cursor;
struct Inspector;

} // namespace vespalib::slime

namespace eval {

struct Value;

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
        bool operator==(const Label &rhs) const noexcept {
            return ((index == rhs.index) &&
                    (name == rhs.name));
        }
        bool operator<(const Label &rhs) const noexcept {
            if (index != rhs.index) {
                return (index < rhs.index);
            }
            return (name < rhs.name);
        }
    };
    struct Value {
        double value;
        Value(double value_in) : value(value_in) {}
        operator double() const { return value; }
        static bool both_nan(double a, double b) {
            return (std::isnan(a) && std::isnan(b));
        }
        bool operator==(const Value &rhs) const {
            return (both_nan(value, rhs.value) || approx_equal(value, rhs.value));
        }
    };
    using Address = std::map<vespalib::string,Label>;
    using Cells = std::map<Address,Value>;
private:
    vespalib::string _type;
    Cells _cells;
public:
    TensorSpec(const vespalib::string &type_spec);
    TensorSpec(const TensorSpec &);
    TensorSpec & operator = (const TensorSpec &);
    ~TensorSpec();
    double as_double() const;
    TensorSpec &add(Address address, double value);
    const vespalib::string &type() const { return _type; }
    const Cells &cells() const { return _cells; }
    vespalib::string to_string() const;
    TensorSpec normalize() const;
    void to_slime(slime::Cursor &tensor) const;
    static TensorSpec from_slime(const slime::Inspector &tensor);
    static TensorSpec from_value(const eval::Value &value);
    static TensorSpec from_expr(const vespalib::string &expr);
};

bool operator==(const TensorSpec &lhs, const TensorSpec &rhs);
std::ostream &operator<<(std::ostream &out, const TensorSpec &tensor);

} // namespace vespalib::eval
} // namespace vespalib
