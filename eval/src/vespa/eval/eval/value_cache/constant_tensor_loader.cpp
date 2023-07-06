// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "constant_tensor_loader.h"
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/data/lz4_input_decoder.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/size_literals.h>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.eval.value_cache.constant_tensor_loader");

namespace vespalib::eval {

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

struct SingleMappedExtractor : ObjectTraverser {
    const vespalib::string &dimension;
    TensorSpec &spec;
    SingleMappedExtractor(const vespalib::string &dimension_in, TensorSpec &spec_in)
        : dimension(dimension_in),
          spec(spec_in)
    {}
    void field(const Memory &symbol, const Inspector &inspector) override {
        vespalib::string label = symbol.make_string();
        double value = inspector.asDouble();
        TensorSpec::Address address;
        address.emplace(dimension, label);
        spec.add(address, value);
    }
};


void decodeSingleMappedForm(const Inspector &root, const ValueType &value_type, TensorSpec &spec) {
    auto extractor = SingleMappedExtractor(value_type.dimensions()[0].name, spec);
    root.traverse(extractor);
}

void decodeSingleDenseForm(const Inspector &values, const ValueType &value_type, TensorSpec &spec) {
    const auto &dimension = value_type.dimensions()[0].name;
    for (size_t i = 0; i < values.entries(); ++i) {
        TensorSpec::Address address;
        address.emplace(dimension, TensorSpec::Label(i));
        spec.add(address, values[i].asDouble());
    }
}

struct DenseValuesDecoder {
    const std::vector<ValueType::Dimension> _idims;
    TensorSpec &_target;
    DenseValuesDecoder(std::vector<ValueType::Dimension> idims, TensorSpec &target)
        : _idims(std::move(idims)),
          _target(target)
    {
    }
    void decode(const Inspector &input, const TensorSpec::Address &address, size_t dim_idx) {
        if (dim_idx == _idims.size()) {
            _target.add(address, input.asDouble());
        } else {
            const auto &dimension = _idims[dim_idx];
            if (input.entries() != dimension.size) {
                return; // TODO: handle mismatch better
            }
            for (size_t i = 0; i < input.entries(); ++i) {
                TensorSpec::Address sub_address = address;
                sub_address.emplace(dimension.name, TensorSpec::Label(i));
                decode(input[i], sub_address, dim_idx + 1);
            }
        }
    }
};

void decodeDenseValues(const Inspector &values, const ValueType &value_type, TensorSpec &spec) {
    TensorSpec::Address address;
    DenseValuesDecoder decoder(value_type.indexed_dimensions(), spec);
    decoder.decode(values, address, 0);
}


template<typename F>
struct TraverserCallback : ObjectTraverser {
    F _f;
    TraverserCallback(F f) : _f(std::move(f)) {}
    void field(const Memory &name, const Inspector &inspector) override {
        _f(name.make_string(), inspector);
    }
};

void decodeSingleMappedBlocks(const Inspector &blocks, const ValueType &value_type, TensorSpec &spec) {
    if (value_type.count_mapped_dimensions() != 1) {
        return; // TODO handle mismatch
    }
    vespalib::string dim_name = value_type.mapped_dimensions()[0].name;
    DenseValuesDecoder decoder(value_type.indexed_dimensions(), spec);
    auto lambda = [&](vespalib::string label, const Inspector &input) {
        TensorSpec::Address address;
        address.emplace(dim_name, std::move(label));
        decoder.decode(input, std::move(address), 0);
    };
    TraverserCallback cb(lambda);
    blocks.traverse(cb);
}

void decodeAddressedBlocks(const Inspector &blocks, const ValueType &value_type, TensorSpec &spec) {
    const auto & idims = value_type.indexed_dimensions();
    std::set<vespalib::string> indexed;
    for (const auto &dimension: idims) {
        indexed.insert(dimension.name);
    }
    DenseValuesDecoder decoder(value_type.indexed_dimensions(), spec);
    for (size_t i = 0; i < blocks.entries(); ++i) {
        TensorSpec::Address address;
        AddressExtractor extractor(indexed, address);
        blocks[i]["address"].traverse(extractor);
        decoder.decode(blocks[i]["values"], address, 0);
    }
}

void decodeLiteralForm(const Inspector &cells, const ValueType &value_type, TensorSpec &spec) {
    std::set<vespalib::string> indexed;
    for (const auto &dimension: value_type.dimensions()) {
        if (dimension.is_indexed()) {
            indexed.insert(dimension.name);
        }
    }
    for (size_t i = 0; i < cells.entries(); ++i) {
        TensorSpec::Address address;
        AddressExtractor extractor(indexed, address);
        cells[i]["address"].traverse(extractor);
        spec.add(address, cells[i]["value"].asDouble());
    }
}

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
            size_t buffer_size = 64_Ki;
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

ConstantTensorLoader::~ConstantTensorLoader() = default;

ConstantValue::UP
ConstantTensorLoader::create(const vespalib::string &path, const vespalib::string &type) const
{
    ValueType value_type = ValueType::from_spec(type);
    if (value_type.is_error()) {
        LOG(warning, "invalid type specification: %s", type.c_str());
        return std::make_unique<BadConstantValue>();
    }
    if (ends_with(path, ".tbf")) {
        vespalib::MappedFileInput file(path);
        vespalib::Memory content = file.get();
        vespalib::nbostream stream(content.data, content.size);
        try {
            return std::make_unique<SimpleConstantValue>(decode_value(stream, _factory));
        } catch (std::exception &) {
            return std::make_unique<BadConstantValue>();
        }
    }
    Slime slime;
    decode_json(path, slime);
    TensorSpec spec(type);
    bool isSingleDenseType = value_type.is_dense() && (value_type.count_indexed_dimensions() == 1);
    bool isSingleMappedType = value_type.is_sparse() && (value_type.count_mapped_dimensions() == 1);
    const Inspector &root = slime.get();
    if (root.type().getId() == vespalib::slime::OBJECT::ID) {
        const Inspector &cells = root["cells"];
        const Inspector &values = root["values"];
        const Inspector &blocks = root["blocks"];
        if (cells.type().getId() == vespalib::slime::ARRAY::ID) {
            decodeLiteralForm(cells, value_type, spec);
        }
        else if (cells.type().getId() == vespalib::slime::OBJECT::ID) {
            if (isSingleMappedType) {
                decodeSingleMappedForm(cells, value_type, spec);
            }
        }
        else if (values.type().getId() == vespalib::slime::ARRAY::ID) {
            decodeDenseValues(values, value_type, spec);
        }
        else if (blocks.type().getId() == vespalib::slime::OBJECT::ID) {
            decodeSingleMappedBlocks(blocks, value_type, spec);
        }
        else if (blocks.type().getId() == vespalib::slime::ARRAY::ID) {
            decodeAddressedBlocks(blocks, value_type, spec);
        }
        else if (isSingleMappedType) {
            decodeSingleMappedForm(root, value_type, spec);
        }
    }
    else if (root.type().getId() == vespalib::slime::ARRAY::ID && isSingleDenseType) {
        decodeSingleDenseForm(root, value_type, spec);
    }
    try {
        return std::make_unique<SimpleConstantValue>(value_from_spec(spec, _factory));
    } catch (std::exception &) {
        return std::make_unique<BadConstantValue>();
    }
}

}
