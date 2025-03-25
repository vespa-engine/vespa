// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sortspec.h"
#include "converters.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/vespalib/text/utf8.h>
#include <cstring>
#include <stdexcept>

namespace search::common {

using vespalib::ConstBufferRef;
using vespalib::make_string;
using sortspec::MissingPolicy;
using sortspec::SortOrder;

ConstBufferRef
PassThroughConverter::onConvert(const ConstBufferRef & src) const
{
    return src;
}

LowercaseConverter::LowercaseConverter() noexcept
    : _buffer()
{
}

ConstBufferRef
LowercaseConverter::onConvert(const ConstBufferRef & src) const
{
    _buffer.clear();
    std::string_view input((const char *)src.data(), src.size());
    vespalib::Utf8Reader r(input);
    vespalib::Utf8Writer w(_buffer);
    while (r.hasMore()) {
        ucs4_t c = r.getChar(0xFFFD);
        c = Fast_NormalizeWordFolder::lowercase_and_fold(c);
        w.putChar(c);
    }
    return {_buffer.data(), _buffer.size()};
}

FieldSortSpec::FieldSortSpec(std::string_view field, bool ascending, std::shared_ptr<BlobConverter> converter) noexcept
    : FieldSortSpec(field, ascending ? SortOrder::ASCENDING : SortOrder::DESCENDING, std::move(converter),
                    sortspec::MissingPolicy::DEFAULT, {})
{
}

FieldSortSpec::FieldSortSpec(std::string_view field, SortOrder sort_order, std::shared_ptr<BlobConverter> converter,
                             MissingPolicy missing_policy, std::string missing_value) noexcept
    : _field(field),
      _ascending(sort_order == SortOrder::ASCENDING),
      _sort_order(sort_order),
      _converter(std::move(converter)),
      _missing_policy(missing_policy),
      _missing_value(std::move(missing_value))
{
}

FieldSortSpec::~FieldSortSpec() = default;

SortSpec::SortSpec()
    : _spec(),
      _field_sort_specs()
{
}

namespace {

std::string_view delimiters(",()\\\" ");

class Tokenizer {
    std::string_view _spec;
    std::string_view::size_type _pos;
public:
    Tokenizer(std::string_view spec)
        : _spec(spec),
          _pos(0)
    {
    }
    ~Tokenizer() = default;
    std::string_view token();
    bool valid() const noexcept { return _pos < _spec.size(); }
    char peek() const noexcept { return valid() ? _spec[_pos] : '\0'; }
    void step() noexcept {
        if (valid()) {
            ++_pos;
        }
    }
    bool skip_spaces() {
        while (valid() && _spec[_pos] == ' ') {
            ++_pos;
        }
        return valid();
    }
    std::string spec() const noexcept;
    void expect_char(char expected);
    char expect_chars(char expected1, char expected2);
    std::string dequote_string();
};

std::string_view
Tokenizer::token()
{
    auto old_pos = _pos;
    _pos = _spec.find_first_of(delimiters, _pos);
    if (_pos == std::string_view::npos) {
        _pos = _spec.size();
    }
    return _spec.substr(old_pos, _pos - old_pos);
}

std::string
Tokenizer::spec() const noexcept
{
    std::string result;
    result.reserve(_spec.size() + 4);
    result.push_back('[');
    result.append(_spec.substr(0, _pos));
    result.push_back(']');
    result.push_back('[');
    result.append(_spec.substr(_pos));
    result.push_back(']');
    return result;
}

void
Tokenizer::expect_char(char expected)
{
    if (!valid()) {
        throw std::runtime_error(make_string("Expected '%c', end of spec reached at %s", expected, spec().c_str()));
    }
    auto act = peek();
    if (act != expected) {
        throw std::runtime_error(make_string("Expected '%c', got '%c' at %s", expected, act, spec().c_str()));
    }
    step();
}

char
Tokenizer::expect_chars(char expected1, char expected2)
{
    if (!valid()) {
        throw std::runtime_error(make_string("Expected '%c' or '%c', end of spec reached at %s", expected1, expected2, spec().c_str()));
    }
    auto act = peek();
    if (act != expected1 && act != expected2) {
        throw std::runtime_error(make_string("Expected '%c' or '%c', got '%c' at %s", expected1, expected2, act,
                                             spec().c_str()));
    }
    step();
    return act;
}

std::string
Tokenizer::dequote_string()
{
    std::string result;
    result.reserve(_spec.size() - _pos);
    expect_char('"');
    while (valid() && peek() != '"') {
        auto fragment = token();
        result.append(fragment);
        if (valid()) {
            auto c = peek();
            if (c == '\\') {
                step();
                c = expect_chars('\\', '"');
                result.push_back(c);
            } else if (c != '"') {
                result.push_back(c);
                step();
            }
        }
    }
    expect_char('"');
    return result;
}

SortOrder decode_sort_order(Tokenizer& tokenizer) {
    auto c = tokenizer.expect_chars('+', '-');
    return (c == '+') ? SortOrder::ASCENDING : SortOrder::DESCENDING;
}

MissingPolicy decode_missing_policy(Tokenizer& tokenizer) {
    auto policy = tokenizer.token();
    if (policy == "first") {
        return MissingPolicy::FIRST;
    } else if (policy == "last") {
        return MissingPolicy::LAST;
    } else if (policy == "as") {
        return MissingPolicy::AS;
    } else {
        std::string missing_policy_copy(policy);
        throw std::runtime_error(make_string("Bad missing policy %s at %s",
            missing_policy_copy.c_str(), tokenizer.spec().c_str()));
    }
}

std::string decode_missing_value(Tokenizer& tokenizer) {
    if (tokenizer.peek() == '"') {
        return tokenizer.dequote_string();
    } else {
        return std::string(tokenizer.token());
    }
}

void decode_missing(Tokenizer& tokenizer, MissingPolicy& missing_policy, std::string& missing_value) {
    tokenizer.expect_char(',');
    missing_policy = decode_missing_policy(tokenizer);
    if (missing_policy == MissingPolicy::AS) {
        tokenizer.expect_char(',');
        missing_value = decode_missing_value(tokenizer);
    }
    tokenizer.expect_char(')');
}

}

SortSpec::SortSpec(const std::string & spec, const ConverterFactory & ucaFactory)
    : _spec(spec),
      _field_sort_specs()
{
    Tokenizer tokenizer(_spec);
    while (tokenizer.skip_spaces()) {
        auto order = decode_sort_order(tokenizer);
        std::string_view attr;
        std::shared_ptr<BlobConverter> converter;
        auto func = tokenizer.token();
        bool in_missing = false;
        if (tokenizer.peek() == '(' && func == "missing") {
            in_missing = true;
            tokenizer.step();
            func = tokenizer.token();
        }
        if (tokenizer.peek() == '(') {
            tokenizer.step();
            if (func == "uca") {
                attr = tokenizer.token();
                tokenizer.expect_char(',');
                auto locale = tokenizer.token();
                std::string_view strength;
                auto c = tokenizer.expect_chars(',', ')');
                if (c == ',') {
                    strength = tokenizer.token();
                    tokenizer.expect_char(')');
                }
                converter = ucaFactory.create(locale, strength);
            } else if (func == "lowercase") {
                attr = tokenizer.token();
                tokenizer.expect_char(')');
                converter = std::make_shared<LowercaseConverter>();
            } else {
                throw std::runtime_error("Unknown func " + std::string(func));
            }
        } else {
            attr = func;
        }
        std::string missing_value;
        MissingPolicy missing_policy = MissingPolicy::DEFAULT;
        if (in_missing) {
            decode_missing(tokenizer, missing_policy, missing_value);
        }
        _field_sort_specs.emplace_back(attr, order, std::move(converter), missing_policy, std::move(missing_value));
    }
}

SortSpec::~SortSpec() = default;

}
