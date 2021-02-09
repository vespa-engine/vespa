// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_model.h"
#include <vespa/eval/eval/value_type.h>

namespace vespalib::eval::test {

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

vespalib::string infer_type(const Layout &layout) {
    return GenSpec(layout.domains).cells(layout.cell_type).type().to_spec();
}

TensorSpec spec(const Layout &layout, const Sequence &seq) {
    return GenSpec(layout.domains).cells(layout.cell_type).seq(seq);
}
TensorSpec spec(const Domain &domain, const Sequence &seq) {
    return spec(Layout({domain}), seq);
}
TensorSpec spec(double value) {
    return GenSpec(value);
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
