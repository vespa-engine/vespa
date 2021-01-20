// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "eval_fixture.h"
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

namespace vespalib {
namespace eval {
namespace test {

//-----------------------------------------------------------------------------

/** define a dimension for a generated TensorSpec */
struct D {
    vespalib::string name;
    bool mapped;
    size_t size;
    size_t stride;
    static D map(const vespalib::string &name_in, size_t size_in, size_t stride_in) { return D{name_in, true, size_in, stride_in}; }
    static D idx(const vespalib::string &name_in, size_t size_in) { return D{name_in, false, size_in, 1}; }
    operator ValueType::Dimension() const {
        if (mapped) {
            return ValueType::Dimension(name);
        } else {
            return ValueType::Dimension(name, size);
        }
    }
    TensorSpec::Label operator()(size_t idx) const {
        if (mapped) {
            // need plain number as string for dynamic sparse peek
            return TensorSpec::Label(fmt("%zu", idx));
        } else {
            return TensorSpec::Label(idx);
        }
    }
};

struct SpecGen {
    TensorSpec spec;

    template <typename ...Ds>
    SpecGen(CellType cell_type, double seq, const Ds &...ds)
      : spec(ValueType::tensor_type({ds...}, cell_type).to_spec())
    {
        add_cells(seq, TensorSpec::Address(), ds...);
    }
    
    template <typename ...Ds>
    SpecGen(double seq, const Ds &...ds)
      : SpecGen(CellType::DOUBLE, seq, {ds...})
    {}

    void add_cells(double &seq, TensorSpec::Address addr) {
        spec.add(addr, seq);
        seq += 1.0;
    }

    template <typename ...Ds> void add_cells(double &seq, TensorSpec::Address addr, const D &d, const Ds &...ds) {
        for (size_t i = 0, idx = 0; i < d.size; ++i, idx += d.stride) {
            addr.insert_or_assign(d.name, d(idx));
            add_cells(seq, addr, ds...);
        }
    }

    template <typename ...Ds>
    static TensorSpec make_spec(CellType cell_type, double seq, const Ds &...ds) {
        SpecGen generate(cell_type, seq, ds...);
        return std::move(generate.spec);
    }

    static TensorSpec make_vector(const D &d1, double seq,
                                  CellType cell_type = CellType::DOUBLE)
    {
        return make_spec(cell_type, seq, d1);
    }
    
    static TensorSpec make_matrix(const D &d1, const D &d2, double seq,
                                  CellType cell_type = CellType::DOUBLE)
    {
        return make_spec(cell_type, seq, d1, d2);
    }
    
    static TensorSpec make_cube(const D &d1, const D &d2, const D &d3, double seq,
                                CellType cell_type = CellType::DOUBLE)
    {
        return make_spec(cell_type, seq, d1, d2, d3);
    }
};

void add_variants(EvalFixture::ParamRepo &repo, vespalib::string name, TensorSpec spec) {
    ValueType orig = ValueType::from_spec(spec.type());
    ValueType flt_type = ValueType::tensor_type(orig.dimensions(), CellType::FLOAT);
    ValueType dbl_type = ValueType::tensor_type(orig.dimensions(), CellType::DOUBLE);
    TensorSpec flt_spec(flt_type.to_spec());
    TensorSpec dbl_spec(flt_type.to_spec());
    for (auto & [addr, val] : spec.cells()) {
        flt_spec.add(addr, val);
        dbl_spec.add(addr, val);
    }
    repo.add(name, dbl_spec);
    repo.add("@" + name, dbl_spec, true);
    repo.add(name + "_f", dbl_spec);
    repo.add("@" + name + "_f", flt_spec, true);
}


} // namespace vespalib::eval::test
} // namespace vespalib::eval
} // namespace vespalib
