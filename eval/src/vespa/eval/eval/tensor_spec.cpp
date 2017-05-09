// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_spec.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <ostream>

namespace vespalib {
namespace eval {

namespace {

TensorSpec::Address extract_address(const slime::Inspector &address) {
    struct Extractor : slime::ObjectTraverser {
        TensorSpec::Address address;
        void field(const Memory &dimension, const slime::Inspector &label) override {
            if (label.type().getId() == slime::STRING::ID) {
                address.emplace(dimension.make_string(), TensorSpec::Label(label.asString().make_string()));
            } else if (label.type().getId() == slime::LONG::ID) {
                address.emplace(dimension.make_string(), TensorSpec::Label(label.asLong()));
            }
        }
    };
    Extractor extractor;
    address.traverse(extractor);
    return extractor.address;
}

} // namespace vespalib::eval::<unnamed>


TensorSpec::TensorSpec(const vespalib::string &type_spec)
    : _type(type_spec),
      _cells()
{ }

TensorSpec::TensorSpec(const TensorSpec &) = default;
TensorSpec & TensorSpec::operator = (const TensorSpec &) = default;

TensorSpec::~TensorSpec() { }

vespalib::string
TensorSpec::to_string() const
{
    vespalib::string out = make_string("spec(%s) {\n", _type.c_str());
    for (const auto &cell: _cells) {
        size_t n = 0;
        out.append("  [");
        for (const auto &label: cell.first) {
            if (n++) {
                out.append(",");
            }
            if (label.second.is_mapped()) {
                out.append(label.second.name);
            } else {
                out.append(make_string("%zu", label.second.index));
            }
        }
        out.append(make_string("]: %g\n", cell.second.value));
    }
    out.append("}");
    return out;
}

void
TensorSpec::to_slime(slime::Cursor &tensor) const
{
    tensor.setString("type", _type);
    slime::Cursor &cells = tensor.setArray("cells");
    for (const auto &my_cell: _cells) {
        slime::Cursor &cell = cells.addObject();
        slime::Cursor &address = cell.setObject("address");
        for (const auto &label: my_cell.first) {
            if (label.second.is_mapped()) {
                address.setString(label.first, label.second.name);
            } else {
                address.setLong(label.first, label.second.index);
            }
        }
        cell.setDouble("value", my_cell.second.value);
    }
}

TensorSpec
TensorSpec::from_slime(const slime::Inspector &tensor)
{
    TensorSpec spec(tensor["type"].asString().make_string());
    const slime::Inspector &cells = tensor["cells"];
    for (size_t i = 0; i < cells.entries(); ++i) {
        const slime::Inspector &cell = cells[i];
        Address address = extract_address(cell["address"]);
        spec.add(address, cell["value"].asDouble());
    }
    return spec;
}

bool
operator==(const TensorSpec &lhs, const TensorSpec &rhs)
{
    return ((lhs.type() == rhs.type()) &&
            (lhs.cells() == rhs.cells()));
}

std::ostream &
operator<<(std::ostream &out, const TensorSpec &spec)
{
    out << spec.to_string();
    return out;
}

} // namespace vespalib::eval
} // namespace vespalib
