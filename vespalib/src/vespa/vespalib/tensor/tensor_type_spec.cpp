// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "tensor_type.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <algorithm>
#include "tensor_type_spec.h"

namespace vespalib {
namespace tensor {
namespace tensor_type {

namespace {

class Tokenizer {
    const char *_cur;
    const char *_end;

    static bool atSpace(char c) { return ((c == ' ') || (c == '\t') ||
                                          (c == '\n') || (c == '\r')); }
    static bool atNumber(char c) { return ((c >= '0') && (c <= '9')); }
    static bool atNameBreak(char c) {
        return (atSpace(c) || (c == '[') || (c == ']') || (c == '{') ||
                (c == '}') || (c == '(') || (c == ')') || (c == ','));
    }
public:
    Tokenizer(const vespalib::string &str)
        : _cur(str.c_str()),
          _end(_cur + str.size())
    {
    }

    bool atEnd() const { return _cur == _end; }
    void step() { ++_cur; }
    void skipSpace() { while (!atEnd() && atSpace(*_cur)) { step(); } }
    char cur() { skipSpace(); return (atEnd() ? '\0' : *_cur); }

    void dimensionNameScan() {
        while (!atEnd() && !atNameBreak(*_cur)) {
            step();
        }
    }

    void numberScan() {
        while (!atEnd() && atNumber(*_cur)) { step(); }
    }

    stringref getDimensionName() {
        skipSpace();
        const char *start = _cur;
        dimensionNameScan();
        return stringref(start, _cur - start);
    }

    stringref getNumber() {
        skipSpace();
        const char *start = _cur;
        numberScan();
        return stringref(start, _cur - start);
    }
};


class Parser
{
    std::vector<TensorType::Dimension> _dimensions;
    vespalib::string _dimensionName;
    vespalib::string _dimensionSizeStr;
    bool _parseError;
    bool _denseDim;
    bool _sparseDim;

    void parseDenseDimension(Tokenizer &tok) {
        tok.step(); // step over open bracket
        _dimensionSizeStr = tok.getNumber();
        if (_dimensionSizeStr.empty() || (tok.cur() != ']')) {
            _parseError = true; // no close bracket or empty dim size
            return;
        }
        tok.step(); // step over close bracket
        long dimensionSize = atol(_dimensionSizeStr.c_str());
        if (dimensionSize <= 0) {
            _parseError = true; // bad dim size
        } else {
            _dimensions.emplace_back(_dimensionName, dimensionSize);
            _denseDim = true;
        }
    }

    void parseSparseDimension(Tokenizer &tok)
    {
        tok.step(); // step over open brace
        if (tok.cur() != '}') {
            _parseError = true; // no close brace
        } else {
            tok.step(); // step over close brace
            _dimensions.emplace_back(_dimensionName);
            _sparseDim = true;
        }
    }

    void parseDimension(Tokenizer &tok) {
        _dimensionName = tok.getDimensionName();
        if (_dimensionName.empty()) {
            _parseError = true; // no dimension name
        } else if (tok.cur() == '[') {
            parseDenseDimension(tok);
        } else if (tok.cur() == '{') {
            parseSparseDimension(tok);
        } else {
            _parseError = true; // no open brace or bracket
        }
    }
public:

    Parser()
        : _dimensions(),
          _dimensionName(),
          _dimensionSizeStr(),
          _parseError(false),
          _denseDim(false),
          _sparseDim(false)
    {
    }

    void parse(Tokenizer &tok) {
        tok.skipSpace();
        _dimensionName = tok.getDimensionName();
        if (_dimensionName != "tensor" || tok.cur() != '(') {
            _parseError = true; // doesn't start with tensor and left parenthesis
        } else {
            tok.step(); // step over left parentesis
        }
        while (!_parseError && tok.cur() != ')') {
            parseDimension(tok);
            if (tok.cur() == ',') {
                tok.step();
                tok.skipSpace();
            } else if (tok.cur() != ')') {
                _parseError = true; // no comma between dimensions
            }
        }
        if (_parseError) {
        } else if (tok.cur() != ')') {
            _parseError = true; // no right parenthesis at end
        } else {
            tok.step(); // step over right parenthesis
            tok.skipSpace();
            if (!tok.atEnd()) {
                _parseError = true; // more unparsed data
            }
        }
    }

    bool invalid() const { return (_parseError || (_denseDim && _sparseDim)); }
    bool sparse() const { return _sparseDim; }
    std::vector<TensorType::Dimension> &dimensions() { return _dimensions; }
};

} // namespace vespalib::tensor::tensor_type::<anonymous>


TensorType
fromSpec(const vespalib::string &str)
{
    Tokenizer tok(str);
    Parser parser;
    parser.parse(tok);
    if (parser.invalid()) {
        return TensorType::invalid();
    }

    if (parser.sparse()) {
        std::vector<vespalib::string> dimensions;
        for (const auto &dim : parser.dimensions()) {
            dimensions.emplace_back(dim.name);
        }
        return TensorType::sparse(dimensions);
    } else {
        return TensorType::dense(parser.dimensions());
    }
}


vespalib::string
toSpec(const TensorType &type)
{
    asciistream os;
    size_t cnt = 0;
    switch (type.type()) {
    case TensorType::Type::INVALID:
        os << "invalid";
        break;
    case TensorType::Type::NUMBER:
        os << "number";
        break;
    case TensorType::Type::SPARSE:
        os << "tensor(";
        for (const auto &d: type.dimensions()) {
            if (cnt++ > 0) {
                os << ",";
            }
            os << d.name << "{}";
        }
        os << ")";
        break;
    case TensorType::Type::DENSE:
        os << "tensor(";
        for (const auto &d: type.dimensions()) {
            if (cnt++ > 0) {
                os << ",";
            }
            os << d.name << "[" << d.size << "]";
        }
        os << ")";
        break;
    }
    return os.str();
}

} // namespace vespalib::tensor::tensor_type
} // namespace vespalib::tensor
} // namespace vespalib
