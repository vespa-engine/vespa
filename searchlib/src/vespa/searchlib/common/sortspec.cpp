// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sortspec.h"

#include "converters.h"

#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <algorithm>
#include <cstring>
#include <stdexcept>

namespace search::common {

using sortspec::ConverterKind;
using sortspec::LevelType;
using sortspec::MissingPolicy;
using sortspec::ParsedSortSpec;
using sortspec::ParsedSortSpecLevel;
using sortspec::SortOrder;

using vespalib::ConstBufferRef;
using vespalib::make_string;

ConstBufferRef PassThroughConverter::onConvert(const ConstBufferRef& src) const {
    return src;
}

LowercaseConverter::LowercaseConverter() noexcept : _buffer() {
}

ConstBufferRef LowercaseConverter::onConvert(const ConstBufferRef& src) const {
    _buffer.clear();
    std::string_view     input((const char*)src.data(), src.size());
    vespalib::Utf8Reader r(input);
    vespalib::Utf8Writer w(_buffer);
    while (r.hasMore()) {
        ucs4_t c = r.getChar(0xFFFD);
        c = Fast_NormalizeWordFolder::lowercase_and_fold(c);
        w.putChar(c);
    }
    return {_buffer.data(), _buffer.size()};
}

namespace sortspec {

ParsedSortSpecLevel::ParsedSortSpecLevel(std::string_view field, SortOrder sort_order) noexcept
    : ParsedSortSpecLevel(LevelType::ATTRIBUTE, field, sort_order) {
}

ParsedSortSpecLevel::ParsedSortSpecLevel(LevelType level_type, std::string_view field, SortOrder sort_order) noexcept
    : _field_or_name(field),
      _sort_order(sort_order),
      _level_type(level_type),
      _missing_policy(MissingPolicy::DEFAULT),
      _converter_kind(ConverterKind::NONE),
      _uca_locale(),
      _uca_strength(),
      _missing_value(),
      _map_key() {
}

ParsedSortSpecLevel::ParsedSortSpecLevel(const ParsedSortSpecLevel&) = default;
ParsedSortSpecLevel::ParsedSortSpecLevel(ParsedSortSpecLevel&&) noexcept = default;
ParsedSortSpecLevel& ParsedSortSpecLevel::operator=(const ParsedSortSpecLevel&) = default;
ParsedSortSpecLevel& ParsedSortSpecLevel::operator=(ParsedSortSpecLevel&&) noexcept = default;
ParsedSortSpecLevel::~ParsedSortSpecLevel() = default;

ParsedSortSpec::ParsedSortSpec() : _spec(), _field_sort_specs() {
}

ParsedSortSpec::ParsedSortSpec(const ParsedSortSpec&) = default;
ParsedSortSpec::ParsedSortSpec(ParsedSortSpec&&) noexcept = default;
ParsedSortSpec& ParsedSortSpec::operator=(const ParsedSortSpec&) = default;
ParsedSortSpec& ParsedSortSpec::operator=(ParsedSortSpec&&) noexcept = default;
ParsedSortSpec::~ParsedSortSpec() = default;

bool ParsedSortSpec::using_rank() const noexcept {
    return std::ranges::any_of(_field_sort_specs, [](const ParsedSortSpecLevel& level) noexcept {
        return level._level_type == LevelType::RANK;
    });
}

} // namespace sortspec

FieldSortSpec::FieldSortSpec(std::string_view field, bool ascending,
                             std::shared_ptr<BlobConverter> converter) noexcept
    : FieldSortSpec(field, ascending ? SortOrder::ASCENDING : SortOrder::DESCENDING, std::move(converter),
                    sortspec::MissingPolicy::DEFAULT, {}, false) {
}

FieldSortSpec::FieldSortSpec(std::string_view field, SortOrder sort_order, std::shared_ptr<BlobConverter> converter,
                             MissingPolicy missing_policy, std::string missing_value, bool is_rank_feature,
                             std::optional<std::string> map_key) noexcept
    : _field(field),
      _sort_order(sort_order),
      _converter(std::move(converter)),
      _missing_policy(missing_policy),
      _missing_value(std::move(missing_value)),
      _is_rank_feature(is_rank_feature),
      _map_key(std::move(map_key)) {
}

FieldSortSpec::~FieldSortSpec() = default;

SortSpec::SortSpec() : _spec(), _field_sort_specs() {
}

namespace {

std::string_view delimiters(",()\\\" ");

class Tokenizer {
    std::string_view            _spec;
    std::string_view::size_type _pos;

public:
    Tokenizer(std::string_view spec) : _spec(spec), _pos(0) {}
    ~Tokenizer() = default;
    std::string_view token();
    bool valid() const noexcept { return _pos < _spec.size(); }
    char peek() const noexcept { return valid() ? _spec[_pos] : '\0'; }
    void step() noexcept {
        if (valid()) {
            ++_pos;
        }
    }
    std::string_view::size_type pos() const noexcept { return _pos; }
    // The text from 'from' up to the next space, or to the end of the spec.
    std::string_view word_at(std::string_view::size_type from) const noexcept {
        auto end = _spec.find(' ', from);
        return _spec.substr(from, (end == std::string_view::npos) ? std::string_view::npos : end - from);
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

    static bool is_identifier_first(char c) noexcept {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }
    static bool is_identifier_rest(char c) noexcept { return is_identifier_first(c) || (c >= '0' && c <= '9'); }

    // Reads an identifier: a letter or '_', then letters, digits or '_'. Returns an
    // empty name, and consumes nothing, if the next char cannot start an identifier.
    std::string_view name() {
        auto old_pos = _pos;
        if (is_identifier_first(peek())) {
            step();
            while (is_identifier_rest(peek())) {
                step();
            }
        }
        return _spec.substr(old_pos, _pos - old_pos);
    }

    // Reads a dotted name: identifiers separated by '.', such as a struct field
    // "person.name". A '.' is consumed only when an identifier follows it. Returns an
    // empty name, and consumes nothing, if the next char cannot start an identifier.
    std::string_view dotted_name() {
        auto old_pos = _pos;
        while (!name().empty()) {
            if (peek() != '.' || _pos + 1 >= _spec.size() || !is_identifier_first(_spec[_pos + 1])) {
                break;
            }
            step();
        }
        return _spec.substr(old_pos, _pos - old_pos);
    }

    std::string_view bracketed() {
        auto old_pos = _pos;
        while (char c = peek()) {
            step();
            if (c == ']') {
                break;
            }
        }
        return _spec.substr(old_pos, _pos - old_pos);
    }
};

std::string_view Tokenizer::token() {
    auto old_pos = _pos;
    _pos = _spec.find_first_of(delimiters, _pos);
    if (_pos == std::string_view::npos) {
        _pos = _spec.size();
    }
    return _spec.substr(old_pos, _pos - old_pos);
}

std::string Tokenizer::spec() const noexcept {
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

void Tokenizer::expect_char(char expected) {
    if (!valid()) {
        throw std::runtime_error(make_string("Expected '%c', end of spec reached at %s", expected, spec().c_str()));
    }
    auto act = peek();
    if (act != expected) {
        throw std::runtime_error(make_string("Expected '%c', got '%c' at %s", expected, act, spec().c_str()));
    }
    step();
}

char Tokenizer::expect_chars(char expected1, char expected2) {
    if (!valid()) {
        throw std::runtime_error(
            make_string("Expected '%c' or '%c', end of spec reached at %s", expected1, expected2, spec().c_str()));
    }
    auto act = peek();
    if (act != expected1 && act != expected2) {
        throw std::runtime_error(
            make_string("Expected '%c' or '%c', got '%c' at %s", expected1, expected2, act, spec().c_str()));
    }
    step();
    return act;
}

std::string Tokenizer::dequote_string() {
    std::string result;
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
        throw std::runtime_error(
            make_string("Unknown missing policy %s at %s", missing_policy_copy.c_str(), tokenizer.spec().c_str()));
    }
}

std::string decode_missing_value(Tokenizer& tokenizer) {
    if (tokenizer.peek() == '"') {
        return tokenizer.dequote_string();
    } else {
        return std::string(tokenizer.token());
    }
}

// An empty strength means PRIMARY. Case matters, as in UcaConverter.
bool is_uca_strength(std::string_view strength) noexcept {
    return strength.empty() || strength == "PRIMARY" || strength == "SECONDARY" || strength == "TERTIARY" ||
           strength == "QUATERNARY" || strength == "IDENTICAL";
}

ParsedSortSpecLevel parseBrackets(Tokenizer& tokenizer, SortOrder sort_order) {
    std::string_view name = tokenizer.bracketed();
    if (name == "[rank]") {
        return ParsedSortSpecLevel(LevelType::RANK, name, sort_order);
    }
    if (name == "[docid]") {
        return ParsedSortSpecLevel(LevelType::DOCID, name, sort_order);
    }
    throw std::runtime_error(make_string("Bad bracketed name in sort spec: %s", std::string(name).c_str()));
}

ParsedSortSpecLevel parseMissing(ParsedSortSpecLevel nested, Tokenizer& tokenizer) {
    if (nested._level_type == LevelType::RANK_FEATURE) {
        throw std::runtime_error("Cannot use missing() with feature(...) sorting");
    }
    tokenizer.expect_char(',');
    nested._missing_policy = decode_missing_policy(tokenizer);
    if (nested._missing_policy == MissingPolicy::AS) {
        tokenizer.expect_char(',');
        nested._missing_value = decode_missing_value(tokenizer);
    }
    tokenizer.expect_char(')');
    return nested;
}

ParsedSortSpecLevel parseFeature(Tokenizer& tokenizer, SortOrder sort_order) {
    std::string_view name = tokenizer.name();
    if (name.empty()) {
        throw std::runtime_error(make_string("Missing feature name in sort spec at: %s", tokenizer.spec().c_str()));
    }
    tokenizer.expect_char(')');
    return ParsedSortSpecLevel{LevelType::RANK_FEATURE, name, sort_order};
}

// Parses the map key after the '{' of m{"k"}. The key must be quoted.
void parseMapKey(ParsedSortSpecLevel& level, Tokenizer& tokenizer) {
    tokenizer.step();
    level._map_key = tokenizer.dequote_string();
    tokenizer.expect_char('}');
}

// A field name, optionally followed by a quoted map key: m{"k"}. The name must not be empty.
ParsedSortSpecLevel parseFieldRef(Tokenizer& tokenizer, SortOrder sort_order) {
    ParsedSortSpecLevel result{tokenizer.dotted_name(), sort_order};
    if (result._field_or_name.empty()) {
        throw std::runtime_error(make_string("Missing name in sort spec at: %s", tokenizer.spec().c_str()));
    }
    if (tokenizer.peek() == '{') {
        parseMapKey(result, tokenizer);
    }
    return result;
}

ParsedSortSpecLevel parseLowercase(Tokenizer& tokenizer, SortOrder sort_order) {
    ParsedSortSpecLevel result = parseFieldRef(tokenizer, sort_order);
    result._converter_kind = ConverterKind::LOWERCASE;
    tokenizer.expect_char(')');
    return result;
}

ParsedSortSpecLevel parseUca(Tokenizer& tokenizer, SortOrder sort_order) {
    ParsedSortSpecLevel result = parseFieldRef(tokenizer, sort_order);
    result._converter_kind = ConverterKind::UCA;
    tokenizer.expect_char(',');
    result._uca_locale = tokenizer.token();
    auto c = tokenizer.expect_chars(',', ')');
    if (c == ',') {
        result._uca_strength = tokenizer.token();
        tokenizer.expect_char(')');
    }
    // Same check and message as UcaConverter, but at parse time.
    if (!is_uca_strength(result._uca_strength)) {
        throw std::runtime_error("Illegal uca collation strength : " + std::string(result._uca_strength));
    }
    return result;
}

// in_missing is set when parsing the argument of missing(...), which can't itself be missing(...).
ParsedSortSpecLevel parseLevel(Tokenizer& tokenizer, SortOrder sort_order, bool in_missing) {
    if (tokenizer.peek() == '[') {
        return parseBrackets(tokenizer, sort_order);
    }
    ParsedSortSpecLevel level = parseFieldRef(tokenizer, sort_order);
    const std::string&  name = level._field_or_name;
    if (tokenizer.peek() != '(') {
        return level;
    }
    // Without this, uca{"k"}(title) would parse as uca(title) and the key would be dropped.
    if (level._map_key.has_value()) {
        throw std::runtime_error(make_string("Map key is not allowed on sort function '%s'", name.c_str()));
    }
    tokenizer.step();
    if (name == "missing") {
        if (in_missing) {
            throw std::runtime_error("Cannot nest missing() inside missing()");
        }
        ParsedSortSpecLevel nested = parseLevel(tokenizer, sort_order, true);
        return parseMissing(std::move(nested), tokenizer);
    }
    if (name == "feature") {
        return parseFeature(tokenizer, sort_order);
    }
    if (name == "lowercase") {
        return parseLowercase(tokenizer, sort_order);
    }
    if (name == "uca") {
        return parseUca(tokenizer, sort_order);
    }
    throw std::runtime_error("Unknown func " + name);
}

ParsedSortSpecLevel parseLevel(Tokenizer& tokenizer) {
    auto order = decode_sort_order(tokenizer);
    return parseLevel(tokenizer, order, false);
}

} // namespace

ParsedSortSpec::ParsedSortSpec(const std::string& spec) : _spec(spec), _field_sort_specs() {
    Tokenizer tokenizer(_spec);
    while (tokenizer.skip_spaces()) {
        auto level_start = tokenizer.pos();
        _field_sort_specs.push_back(parseLevel(tokenizer));
        // Levels are separated by spaces. Without this check, +a-b would parse as +a -b.
        if (tokenizer.valid() && tokenizer.peek() != ' ') {
            throw std::runtime_error(make_string("Unexpected '%s' after sort level in '%s'",
                                                 std::string(tokenizer.word_at(tokenizer.pos())).c_str(),
                                                 std::string(tokenizer.word_at(level_start)).c_str()));
        }
    }
}

SortSpec::SortSpec(const ParsedSortSpec& parsed, const ConverterFactory& ucaFactory)
    : _spec(parsed.getSpec()), _field_sort_specs() {
    _field_sort_specs.reserve(parsed.size());
    for (const auto& field : parsed) {
        std::shared_ptr<BlobConverter> converter;
        switch (field._converter_kind) {
        case ConverterKind::NONE:
            break;
        case ConverterKind::LOWERCASE:
            converter = std::make_shared<LowercaseConverter>();
            break;
        case ConverterKind::UCA:
            converter = ucaFactory.create(field._uca_locale, field._uca_strength);
            break;
        }
        _field_sort_specs.emplace_back(field._field_or_name, field._sort_order, std::move(converter),
                                       field._missing_policy, field._missing_value,
                                       field._level_type == LevelType::RANK_FEATURE, field._map_key);
    }
}

SortSpec::SortSpec(const std::string& spec, const ConverterFactory& ucaFactory)
    : SortSpec(ParsedSortSpec(spec), ucaFactory) {
}

SortSpec::~SortSpec() = default;

} // namespace search::common
