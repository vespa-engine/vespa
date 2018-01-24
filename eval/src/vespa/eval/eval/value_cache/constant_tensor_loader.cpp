// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "constant_tensor_loader.h"
#include <vespa/eval/eval/tensor.h>
#include <vespa/eval/eval/tensor_engine.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/data/lz4_input_decoder.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <set>
#include <vector>

#include <vespa/log/log.h>
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

void decode_json(const vespalib::string &path, Input &input, Slime &slime) {
    if (slime::JsonFormat::decode(input, slime) == 0) {
        LOG(warning, "file contains invalid json: %s", path.c_str());
    }
}

void decode_json(const vespalib::string &path, Slime &slime) {
    MappedFileInput file(path);
    if (!file.valid()) {
        LOG(warning, "could not read file: %s", path.c_str());
    } else {
        if (ends_with(path, ".lz4")) {
            size_t buffer_size = 64 * 1024;
            Lz4InputDecoder lz4_decoder(file, buffer_size);
            decode_json(path, lz4_decoder, slime);
            if (lz4_decoder.failed()) {
                LOG(warning, "file contains lz4 errors (%s): %s",
                    lz4_decoder.reason().c_str(), path.c_str());
            }
        } else {
            decode_json(path, file, slime);
        }
    }
}

} // namespace vespalib::eval::<unnamed>

ConstantValue::UP
ConstantTensorLoader::create(const vespalib::string &path, const vespalib::string &type) const
{
    ValueType value_type = ValueType::from_spec(type);
    if (value_type.is_error()) {
        LOG(warning, "invalid type specification: %s", type.c_str());
        return std::make_unique<SimpleConstantValue>(_engine.from_spec(TensorSpec("double")));
    }
    if (ends_with(path, ".tbf")) {
        vespalib::MappedFileInput file(path);
        vespalib::Memory content = file.get();
        vespalib::nbostream stream(content.data, content.size);
        return std::make_unique<SimpleConstantValue>(_engine.decode(stream));
    }
    Slime slime;
    decode_json(path, slime);
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
    return std::make_unique<SimpleConstantValue>(_engine.from_spec(spec));
}

} // namespace vespalib::eval
} // namespace vespalib
