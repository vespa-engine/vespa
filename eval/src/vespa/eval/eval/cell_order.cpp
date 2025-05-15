// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cell_order.h"

namespace vespalib::eval {

std::string as_string(CellOrder cell_order) {
    switch (cell_order) {
        case CellOrder::MAX: return "max";
        case CellOrder::MIN: return "min";
    }
    abort();
}

std::optional<CellOrder> cell_order_from_string(const std::string &str) {
    if (str == "max") {
        return CellOrder::MAX;
    } else if (str == "min") {
        return CellOrder::MIN;
    } else {
        return std::nullopt;
    }
}

} // namespace
