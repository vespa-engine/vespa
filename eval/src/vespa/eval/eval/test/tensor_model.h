// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "gen_spec.h"

namespace vespalib::eval::test {

// custom op1
struct MyOp {
    static double f(double a) {
        return ((a + 1) * 2);
    }
};

// 'a in [1,5,7,13,42]'
struct MyIn {
    static double f(double a) {
        if ((a ==  1) ||
            (a ==  5) ||
            (a ==  7) ||
            (a == 13) ||
            (a == 42))
        {
            return 1.0;
        } else {
            return 0.0;
        }
    }
};

using Domain = DimSpec;

struct Layout {
    CellType cell_type;
    std::vector<Domain> domains;
    Layout(std::initializer_list<Domain> domains_in)
        : cell_type(CellType::DOUBLE), domains(domains_in) {}
    Layout(CellType cell_type_in, std::vector<Domain> domains_in)
        : cell_type(cell_type_in), domains(std::move(domains_in)) {}
    auto begin() const { return domains.begin(); }
    auto end() const { return domains.end(); }
    auto size() const { return domains.size(); }
    auto operator[](size_t idx) const { return domains[idx]; }
};

Layout float_cells(const Layout &layout);

Domain x();
Domain x(size_t size);
Domain x(const std::vector<vespalib::string> &keys);

Domain y();
Domain y(size_t size);
Domain y(const std::vector<vespalib::string> &keys);

Domain z();
Domain z(size_t size);
Domain z(const std::vector<vespalib::string> &keys);

// Infer the tensor type implied by the given layout
vespalib::string infer_type(const Layout &layout);

TensorSpec spec(const Layout &layout, const Sequence &seq);
TensorSpec spec(const Domain &domain, const Sequence &seq);
TensorSpec spec(double value);
TensorSpec spec(const vespalib::string &type,
                const std::vector<std::pair<TensorSpec::Address, TensorSpec::Value>> &cells);
TensorSpec spec(const vespalib::string &value_expr);

}
