// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_type.h"
#include "value_type_spec.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <algorithm>

namespace vespalib::eval::value_type {

vespalib::string cell_type_to_name(CellType cell_type) {
    switch (cell_type) {
    case CellType::DOUBLE: return "double";
    case CellType::FLOAT: return "float";
    }
    abort();
}

std::optional<CellType> cell_type_from_name(const vespalib::string &name) {
    for (CellType t : CellTypeUtils::list_types()) {
        if (name == cell_type_to_name(t)) {
            return t;
        }
    }
    return std::nullopt;
}

namespace {

class ParseContext
{
public:
    struct Mark {
        const char *pos;
        char curr;
        bool failed;
        Mark(const char *pos_in, char curr_in, bool failed_in)
            : pos(pos_in), curr(curr_in), failed(failed_in) {}
    };
private:
    const char  *_pos;
    const char  *_end;
    const char *&_pos_after;
    char         _curr;
    bool         _failed;

public:
    ParseContext(const char *pos, const char *end, const char *&pos_out)
        : _pos(pos), _end(end), _pos_after(pos_out), _curr(0), _failed(false)
    {
        if (_pos < _end) {
            _curr = *_pos;
        }
    }
    ~ParseContext() {
        if (!_failed) {
            _pos_after = _pos;
        } else {
            _pos_after = nullptr;
        }
    }
    Mark mark() const {
        return Mark(_pos, _curr, _failed);
    }
    void revert(Mark mark) {
        _pos = mark.pos;
        _curr = mark.curr;
        _failed = mark.failed;
    }
    void fail() {
        _failed = true;
        _curr = 0;
    }
    bool failed() const { return _failed; }
    void next() { _curr = (_curr && (_pos < _end)) ? *(++_pos) : 0; }
    char get() const { return _curr; }
    bool eos() const { return !_curr; }
    void eat(char c) {
        if (_curr == c) {
            next();
        } else {
            fail();
        }
    }
    void skip_spaces() {
        while (!eos() && isspace(_curr)) {
            next();
        }
    }
};

bool is_ident(char c, bool first) {
    return ((c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z') ||
            (c == '_') ||
            (c >= '0' && c <= '9' && !first));
}

vespalib::string parse_ident(ParseContext &ctx) {
    ctx.skip_spaces();
    vespalib::string ident;
    if (is_ident(ctx.get(), true)) {
        ident.push_back(ctx.get());
        for (ctx.next(); is_ident(ctx.get(), false); ctx.next()) {
            ident.push_back(ctx.get());
        }
    }
    ctx.skip_spaces();
    return ident;
}

size_t parse_int(ParseContext &ctx) {
    ctx.skip_spaces();
    vespalib::string num;
    for (; isdigit(ctx.get()); ctx.next()) {
        num.push_back(ctx.get());
    }
    if (num.empty()) {
        ctx.fail();
    }
    return atoi(num.c_str());
}

ValueType::Dimension parse_dimension(ParseContext &ctx) {
    ValueType::Dimension dimension(parse_ident(ctx));
    ctx.skip_spaces();
    if (ctx.get() == '{') {
        ctx.eat('{');
        ctx.skip_spaces();
        ctx.eat('}');
    } else if (ctx.get() == '[') {
        ctx.eat('[');
        ctx.skip_spaces();
        if (ctx.get() == ']') {
            dimension.size = 0;
        } else {
            dimension.size = parse_int(ctx);
            ctx.skip_spaces();            
        }
        ctx.eat(']');
    } else {
        ctx.fail();
    }
    return dimension;
}

std::vector<ValueType::Dimension> parse_dimension_list(ParseContext &ctx) {
    std::vector<ValueType::Dimension> list;
    ctx.skip_spaces();
    ctx.eat('(');
    ctx.skip_spaces();
    while (!ctx.eos() && (ctx.get() != ')')) {
        if (!list.empty()) {
            ctx.eat(',');
        }
        list.push_back(parse_dimension(ctx));
        ctx.skip_spaces();
    }
    ctx.eat(')');
    ctx.skip_spaces();
    return list;
}

CellType parse_cell_type(ParseContext &ctx) {
    auto mark = ctx.mark();
    ctx.skip_spaces();
    ctx.eat('<');
    auto cell_type = parse_ident(ctx);
    ctx.skip_spaces();
    ctx.eat('>');
    if (ctx.failed()) {
        ctx.revert(mark);
    } else {
        auto result = cell_type_from_name(cell_type);
        if (result.has_value()) {
            return result.value();
        } else {
            ctx.fail();
        }
    }
    return CellType::DOUBLE;
}

} // namespace vespalib::eval::value_type::<anonymous>

ValueType
parse_spec(const char *pos_in, const char *end_in, const char *&pos_out,
           std::vector<ValueType::Dimension> *unsorted)
{
    ParseContext ctx(pos_in, end_in, pos_out);
    vespalib::string type_name = parse_ident(ctx);
    if (type_name == "error") {
        return ValueType::error_type();
    } else if (type_name == "double") {
        return ValueType::double_type();
    } else if (type_name == "tensor") {
        CellType cell_type = parse_cell_type(ctx);
        std::vector<ValueType::Dimension> list = parse_dimension_list(ctx);
        if (!ctx.failed()) {
            if (unsorted != nullptr) {
                *unsorted = list;
            }
            return ValueType::make_type(cell_type, std::move(list));
        }
    } else {
        ctx.fail();
    }
    return ValueType::error_type();
}

ValueType
from_spec(const vespalib::string &spec)
{
    const char *after = nullptr;
    const char *end = spec.data() + spec.size();
    ValueType type = parse_spec(spec.data(), end, after);
    if (after != end) {
        return ValueType::error_type();
    }
    return type;
}

ValueType
from_spec(const vespalib::string &spec, std::vector<ValueType::Dimension> &unsorted)
{
    const char *after = nullptr;
    const char *end = spec.data() + spec.size();
    ValueType type = parse_spec(spec.data(), end, after, &unsorted);
    if (after != end) {
        return ValueType::error_type();
    }
    return type;
}

vespalib::string
to_spec(const ValueType &type)
{
    asciistream os;
    size_t cnt = 0;
    if (type.is_error()) {
        os << "error";
    } else if (type.is_double()) {
        os << "double";
    } else {
        os << "tensor";
        if (type.cell_type() != CellType::DOUBLE) {
            os << "<" << cell_type_to_name(type.cell_type()) << ">";
        }
        os << "(";
        for (const auto &d: type.dimensions()) {
            if (cnt++ > 0) {
                os << ",";
            }
            if (d.size == ValueType::Dimension::npos) {
                os << d.name << "{}";
            } else {
                os << d.name << "[" << d.size << "]";
            }
        }
        os << ")";
    }
    return os.str();
}

}
