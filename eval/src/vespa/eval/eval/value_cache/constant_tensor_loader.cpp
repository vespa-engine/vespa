// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

struct Target {
    const ValueType tensor_type;
    TensorSpec spec;
    void check_add(TensorSpec::Address address, double value) {
        for (const auto &dim : tensor_type.dimensions()) {
            const auto & it = address.find(dim.name);
            if (it == address.end()) {
                LOG(error, "Missing dimension '%s' in address for constant tensor", dim.name.c_str());
                throw std::exception();
            }
            if (it->second.is_mapped() != dim.is_mapped()) {
                LOG(error, "Mismatch mapped/indexed for '%s' in address", dim.name.c_str());
                throw std::exception();
            }
            if (dim.is_indexed()) {
                if (it->second.index >= dim.size) {
                    LOG(error, "Index %zu out of range for dimension %s[%u]",
                        it->second.index, dim.name.c_str(), dim.size);
                    throw std::exception();
                }
            }
        }
        if (address.size() != tensor_type.dimensions().size()) {
            for (const auto & [name, label] : address) {
                if (tensor_type.dimension_index(name) == ValueType::Dimension::npos) {
                    LOG(error, "Extra dimension '%s' in address for constant tensor", name.c_str());
                }
            }
            LOG(error, "Wrong number %zu of dimensions in address for constant tensor, wanted %zu",
                address.size(), tensor_type.dimensions().size());
            throw std::exception();
        }
        spec.add(address, value);
    }
};

struct AddressExtractor : ObjectTraverser {
    const std::set<vespalib::string> &indexed;
    TensorSpec::Address &address;
    AddressExtractor(const std::set<vespalib::string> &indexed_in,
                     TensorSpec::Address &address_out)
        : indexed(indexed_in), address(address_out) {}
    void field(const Memory &symbol, const Inspector &inspector) override {
        vespalib::string dimension = symbol.make_string();
        if (dimension.empty()) {
            LOG(warning, "missing 'dimension' in address");
            throw std::exception();
        }
        if (inspector.type().getId() == vespalib::slime::LONG::ID) {
            size_t index = inspector.asLong();
            if (indexed.contains(dimension)) {
                address.emplace(dimension, TensorSpec::Label(index));
            } else {
                auto label = std::to_string(index);
                address.emplace(dimension, TensorSpec::Label(label));
            }
            return;
        }
        vespalib::string label = inspector.asString().make_string();
        if (label.empty()) {
            auto got = inspector.toString();
            int sz = got.size();
            if (sz > 0) --sz;
            LOG(error, "missing 'label' in address, got '%.*s'", sz, got.c_str());
            throw std::exception();
        }
        if (indexed.find(dimension) == indexed.end()) {
            address.emplace(dimension, TensorSpec::Label(label));
        } else {
            const char *str_beg = label.c_str();
            char *str_end = const_cast<char *>(str_beg);
            size_t index = strtoull(str_beg, &str_end, 10);
            if (str_end == str_beg || *str_end != '\0') {
                LOG(error, "bad index: '%s' cannot be parsed as an unsigned integer", str_beg);
                throw std::exception();
            }
            address.emplace(dimension, TensorSpec::Label(index));
        }
    }
};

struct SingleMappedExtractor : ObjectTraverser {
    const vespalib::string &dimension;
    Target &target;
    SingleMappedExtractor(const vespalib::string &dimension_in, Target &target_in)
        : dimension(dimension_in),
          target(target_in)
    {}
    void field(const Memory &symbol, const Inspector &inspector) override {
        vespalib::string label = symbol.make_string();
        double value = inspector.asDouble();
        TensorSpec::Address address;
        address.emplace(dimension, label);
        target.check_add(address, value);
    }
};


void decodeSingleMappedForm(const Inspector &root, const ValueType &value_type, Target &target) {
    auto extractor = SingleMappedExtractor(value_type.dimensions()[0].name, target);
    root.traverse(extractor);
}

void decodeSingleDenseForm(const Inspector &values, const ValueType &value_type, Target &target) {
    const auto &dimension = value_type.dimensions()[0].name;
    for (size_t i = 0; i < values.entries(); ++i) {
        TensorSpec::Address address;
        address.emplace(dimension, TensorSpec::Label(i));
        target.check_add(address, values[i].asDouble());
    }
}

struct DenseValuesDecoder {
    const std::vector<ValueType::Dimension> _idims;
    Target &_target;
    void decode(const Inspector &input, const TensorSpec::Address &address, size_t dim_idx) {
        if (dim_idx == _idims.size()) {
            _target.check_add(address, input.asDouble());
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

void decodeDenseValues(const Inspector &values, const ValueType &value_type, Target &target) {
    TensorSpec::Address address;
    DenseValuesDecoder decoder{value_type.indexed_dimensions(), target};
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

void decodeSingleMappedBlocks(const Inspector &blocks, const ValueType &value_type, Target &target) {
    if (value_type.count_mapped_dimensions() != 1) {
        return; // TODO handle mismatch
    }
    vespalib::string dim_name = value_type.mapped_dimensions()[0].name;
    DenseValuesDecoder decoder{value_type.indexed_dimensions(), target};
    auto lambda = [&](vespalib::string label, const Inspector &input) {
        TensorSpec::Address address;
        address.emplace(dim_name, std::move(label));
        decoder.decode(input, std::move(address), 0);
    };
    TraverserCallback cb(lambda);
    blocks.traverse(cb);
}

void decodeAddressedBlocks(const Inspector &blocks, const ValueType &value_type, Target &target) {
    const auto & idims = value_type.indexed_dimensions();
    std::set<vespalib::string> indexed;
    for (const auto &dimension: idims) {
        indexed.insert(dimension.name);
    }
    DenseValuesDecoder decoder{value_type.indexed_dimensions(), target};
    for (size_t i = 0; i < blocks.entries(); ++i) {
        TensorSpec::Address address;
        AddressExtractor extractor(indexed, address);
        blocks[i]["address"].traverse(extractor);
        decoder.decode(blocks[i]["values"], address, 0);
    }
}

void decodeLiteralForm(const Inspector &cells, const ValueType &value_type, Target &target) {
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
        target.check_add(address, cells[i]["value"].asDouble());
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
    Target target{value_type, TensorSpec(type)};
    bool isSingleDenseType = value_type.is_dense() && (value_type.count_indexed_dimensions() == 1);
    bool isSingleMappedType = value_type.is_sparse() && (value_type.count_mapped_dimensions() == 1);
    const Inspector &root = slime.get();
    if (root.type().getId() == vespalib::slime::OBJECT::ID) {
        const Inspector &cells = root["cells"];
        const Inspector &values = root["values"];
        const Inspector &blocks = root["blocks"];
        if (cells.type().getId() == vespalib::slime::ARRAY::ID) {
            decodeLiteralForm(cells, value_type, target);
        }
        else if (cells.type().getId() == vespalib::slime::OBJECT::ID) {
            if (isSingleMappedType) {
                decodeSingleMappedForm(cells, value_type, target);
            }
        }
        else if (values.type().getId() == vespalib::slime::ARRAY::ID) {
            decodeDenseValues(values, value_type, target);
        }
        else if (blocks.type().getId() == vespalib::slime::OBJECT::ID) {
            decodeSingleMappedBlocks(blocks, value_type, target);
        }
        else if (blocks.type().getId() == vespalib::slime::ARRAY::ID) {
            decodeAddressedBlocks(blocks, value_type, target);
        }
        else if (isSingleMappedType) {
            decodeSingleMappedForm(root, value_type, target);
        }
    }
    else if (root.type().getId() == vespalib::slime::ARRAY::ID && isSingleDenseType) {
        decodeSingleDenseForm(root, value_type, target);
    }
    try {
        return std::make_unique<SimpleConstantValue>(value_from_spec(target.spec, _factory));
    } catch (std::exception &) {
        return std::make_unique<BadConstantValue>();
    }
}

}
