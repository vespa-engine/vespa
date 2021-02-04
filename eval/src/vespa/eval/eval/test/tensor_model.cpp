// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_model.h"
#include <vespa/eval/eval/value_type.h>

namespace vespalib::eval::test {

Domain::Domain(const Domain &) = default;
Domain::~Domain() = default;

Layout float_cells(const Layout &layout) {
    return Layout(CellType::FLOAT, layout.domains);
}

Domain x() { return Domain("x", {}); }
Domain x(size_t size) { return Domain("x", size); }
Domain x(const std::vector<vespalib::string> &keys) { return Domain("x", keys); }

Domain y() { return Domain("y", {}); }
Domain y(size_t size) { return Domain("y", size); }
Domain y(const std::vector<vespalib::string> &keys) { return Domain("y", keys); }

Domain z() { return Domain("z", {}); }
Domain z(size_t size) { return Domain("z", size); }
Domain z(const std::vector<vespalib::string> &keys) { return Domain("z", keys); }

// Infer the tensor type spanned by the given spaces
vespalib::string infer_type(const Layout &layout) {
    std::vector<ValueType::Dimension> dimensions;
    for (const auto &domain: layout) {
        if (domain.size == 0) {
            dimensions.emplace_back(domain.dimension); // mapped
        } else {
            dimensions.emplace_back(domain.dimension, domain.size); // indexed
        }
    }
    return ValueType::tensor_type(dimensions, layout.cell_type).to_spec();
}

// Wrapper for the things needed to generate a tensor
struct Source {
    using Address = TensorSpec::Address;

    const Layout   &layout;
    const Sequence &seq;
    Source(const Layout &layout_in, const Sequence &seq_in)
        : layout(layout_in), seq(seq_in) {}
};

// Mix layout with a number sequence to make a tensor spec
class TensorSpecBuilder
{
private:
    using Label = TensorSpec::Label;
    using Address = TensorSpec::Address;

    Source     _source;
    TensorSpec _spec;
    Address    _addr;
    size_t     _idx;

    void generate(size_t layout_idx) {
        if (layout_idx == _source.layout.size()) {
            _spec.add(_addr, _source.seq(_idx++));
        } else {
            const Domain &domain = _source.layout[layout_idx];
            if (domain.size > 0) { // indexed
                for (size_t i = 0; i < domain.size; ++i) {
                    _addr.emplace(domain.dimension, Label(i)).first->second = Label(i);
                    generate(layout_idx + 1);
                }
            } else { // mapped
                for (const vespalib::string &key: domain.keys) {
                    _addr.emplace(domain.dimension, Label(key)).first->second = Label(key);
                    generate(layout_idx + 1);
                }
            }
        }
    }

public:
    TensorSpecBuilder(const Layout &layout, const Sequence &seq)
        : _source(layout, seq), _spec(infer_type(layout)), _addr(), _idx(0) {}
    TensorSpec build() {
        generate(0);
        return _spec;
    }
};

TensorSpec spec(const Layout &layout, const Sequence &seq) {
    return TensorSpecBuilder(layout, seq).build();
}
TensorSpec spec(const Domain &domain, const Sequence &seq) {
    return TensorSpecBuilder(Layout({domain}), seq).build();
}
TensorSpec spec(double value) {
    return spec(Layout({}), Seq({value}));
}

TensorSpec spec(const vespalib::string &type,
                const std::vector<std::pair<TensorSpec::Address, TensorSpec::Value>> &cells) {
    TensorSpec spec("tensor(" + type + ")");

    for (const auto &cell : cells) {
        spec.add(cell.first, cell.second);
    }
    return spec;
}

TensorSpec spec(const vespalib::string &value_expr) {
    return TensorSpec::from_expr(value_expr);
}

}
