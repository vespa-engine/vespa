// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>

#include "constant_tensor_loader.h"
#include <set>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/eval/eval/tensor.h>
#include <vespa/eval/eval/tensor_engine.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/vespalib/io/mapped_file_input.h>

LOG_SETUP(".vespalib.eval.value_cache.constant_tensor_loader");

namespace vespalib {
namespace eval {

using Inspector = slime::Inspector;
using ObjectTraverser = slime::ObjectTraverser;

namespace {

struct AddressExtractor : ObjectTraverser {
    const std::set<vespalib::string> &indexed;
    TensorSpec::Address &address;
    AddressExtractor(const std::set<vespalib::string> &indexed_in,
                     TensorSpec::Address &address_out)
        : indexed(indexed_in), address(address_out) {}
    void field(const Memory &symbol, const Inspector &inspector) override {
        vespalib::string dimension = symbol.make_string();
        vespalib::string label = inspector.asString().make_string();
        if (dimension.empty() || label.empty()) {
            return;
        }
        if (indexed.find(dimension) == indexed.end()) {
            address.emplace(dimension, TensorSpec::Label(label));
        } else {
            size_t index = strtoull(label.c_str(), nullptr, 10);
            address.emplace(dimension, TensorSpec::Label(index));
        }
    }
};

} // namespace vespalib::eval::<unnamed>

using ErrorConstant = SimpleConstantValue<ErrorValue>;
using TensorConstant = SimpleConstantValue<TensorValue>;

ConstantValue::UP
ConstantTensorLoader::create(const vespalib::string &path, const vespalib::string &type) const
{
    ValueType value_type = ValueType::from_spec(type);
    if (value_type.is_error()) {
        LOG(warning, "invalid type specification: %s", type.c_str());
        auto tensor = _engine.create(TensorSpec("double"));
        return std::make_unique<TensorConstant>(_engine.type_of(*tensor), std::move(tensor));
    }
    Slime slime;
    MappedFileInput file(path);
    if (!file.valid()) {
        LOG(warning, "could not read file: %s", path.c_str());
    } else if (slime::JsonFormat::decode(file.get(), slime) == 0) {
        LOG(warning, "file contains invalid json: %s", path.c_str());
    }
    std::set<vespalib::string> indexed;
    for (const auto &dimension: value_type.dimensions()) {
        if (dimension.is_indexed()) {
            indexed.insert(dimension.name);
        }
    }
    TensorSpec spec(type);
    const Inspector &cells = slime.get()["cells"];
    for (size_t i = 0; i < cells.entries(); ++i) {
        TensorSpec::Address address;
        AddressExtractor extractor(indexed, address);
        cells[i]["address"].traverse(extractor);
        spec.add(address, cells[i]["value"].asDouble());
    }
    auto tensor = _engine.create(spec);
    return std::make_unique<TensorConstant>(_engine.type_of(*tensor), std::move(tensor));
}

} // namespace vespalib::eval
} // namespace vespalib
