// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace search::common {

class BlobConverter;
class ConverterFactory;

namespace sortspec {

enum class SortOrder : uint8_t { ASCENDING, DESCENDING };

enum class LevelType : uint8_t { ATTRIBUTE, RANK_FEATURE, RANK, DOCID };

enum class MissingPolicy : uint8_t {
    DEFAULT, // Single value: first on ascending, last on descending. Multi value: last
    FIRST,
    LAST,
    AS
};

// Which string converter a sort level asks for. The converter itself is created
// when a ParsedSortSpec is turned into a SortSpec.
enum class ConverterKind : uint8_t { NONE, LOWERCASE, UCA };

enum class UcaStrength : uint8_t { PRIMARY, SECONDARY, TERTIARY, QUATERNARY, IDENTICAL };

// Maps a uca strength name, as written in uca(field,locale,strength), to a UcaStrength.
// An empty name means PRIMARY. Case matters. Throws std::runtime_error on an unknown name.
// Both the sort spec parser and UcaConverter use this, so they accept the same names.
UcaStrength to_uca_strength(std::string_view name);

// The attributes holding the keys and the values of the map named in m{"k"}.
std::string map_key_attribute_name(std::string_view map_name);
std::string map_value_attribute_name(std::string_view map_name);

// One sort level as written in the sort spec, before any converter is created.
struct ParsedSortSpecLevel {
    ParsedSortSpecLevel(std::string_view field, sortspec::SortOrder sort_order) noexcept;
    ParsedSortSpecLevel(sortspec::LevelType level_type, std::string_view name,
                        sortspec::SortOrder sort_order) noexcept;
    ParsedSortSpecLevel(const ParsedSortSpecLevel&);
    ParsedSortSpecLevel(ParsedSortSpecLevel&&) noexcept;
    ParsedSortSpecLevel& operator=(const ParsedSortSpecLevel&);
    ParsedSortSpecLevel& operator=(ParsedSortSpecLevel&&) noexcept;
    ~ParsedSortSpecLevel();
    std::string             _field_or_name;
    sortspec::SortOrder     _sort_order;
    sortspec::LevelType     _level_type;
    sortspec::MissingPolicy _missing_policy;
    sortspec::ConverterKind _converter_kind;
    // Only used when _converter_kind is UCA.
    std::string _uca_locale;
    std::string _uca_strength;
    std::string _missing_value;
    // Set for m{"k"}: _field is the map name, and the value is read from <map>.value at this key.
    std::optional<std::string> _map_key;
    bool is_ascending() const noexcept { return _sort_order == sortspec::SortOrder::ASCENDING; }
    bool is_rank_feature() const noexcept { return _level_type == LevelType::RANK_FEATURE; }
};

// The result of parsing a sort spec. Parsing needs no converter factory, so this can be
// built once and turned into a SortSpec wherever converters are needed.
class ParsedSortSpec {
    std::string                      _spec;
    std::vector<ParsedSortSpecLevel> _field_sort_specs;

public:
    ParsedSortSpec();
    // Throws std::runtime_error if the spec is malformed. An empty spec, or one with
    // only spaces, is not an error; it gives no levels, which callers check with empty().
    explicit ParsedSortSpec(const std::string& spec);
    ParsedSortSpec(const ParsedSortSpec&);
    ParsedSortSpec(ParsedSortSpec&&) noexcept;
    ParsedSortSpec& operator=(const ParsedSortSpec&);
    ParsedSortSpec& operator=(ParsedSortSpec&&) noexcept;
    ~ParsedSortSpec();
    const std::string& getSpec() const noexcept { return _spec; }
    size_t size() const noexcept { return _field_sort_specs.size(); }
    bool empty() const noexcept { return _field_sort_specs.empty(); }
    // True if some level sorts by [rank], also inside missing(...).
    bool using_rank() const noexcept;
    const ParsedSortSpecLevel& operator[](size_t idx) const noexcept { return _field_sort_specs[idx]; }
    auto begin() const noexcept { return _field_sort_specs.begin(); }
    auto end() const noexcept { return _field_sort_specs.end(); }
};

} // namespace sortspec

struct FieldSortSpec {
    FieldSortSpec(std::string_view field, bool ascending, std::shared_ptr<BlobConverter> converter) noexcept;
    FieldSortSpec(std::string_view field, sortspec::SortOrder, std::shared_ptr<BlobConverter> converter,
                  sortspec::MissingPolicy missing_policy, std::string missing_value, bool is_rank_feature = false,
                  std::optional<std::string> map_key = std::nullopt) noexcept;
    ~FieldSortSpec();
    std::string                    _field;
    sortspec::SortOrder            _sort_order;
    std::shared_ptr<BlobConverter> _converter;
    sortspec::MissingPolicy        _missing_policy;
    std::string                    _missing_value;
    bool                           _is_rank_feature;
    // Set for m{"k"}: _field is the map name, and the value is read from <map>.value at this key.
    std::optional<std::string> _map_key;
    bool is_ascending() const noexcept { return _sort_order == sortspec::SortOrder::ASCENDING; }
};

class SortSpec {
    std::string                _spec;
    std::vector<FieldSortSpec> _field_sort_specs;

public:
    SortSpec();
    // Creates the converters for each level. The factory may throw on an unknown uca strength.
    SortSpec(const sortspec::ParsedSortSpec& parsed, const ConverterFactory& ucaFactory);
    // Parses and creates converters in one step. Throws std::runtime_error on a malformed spec.
    SortSpec(const std::string& spec, const ConverterFactory& ucaFactory);
    ~SortSpec();
    const std::string& getSpec() const noexcept { return _spec; }
    size_t size() const noexcept { return _field_sort_specs.size(); }
    bool empty() const noexcept { return _field_sort_specs.empty(); }
    const FieldSortSpec& operator[](size_t idx) const noexcept { return _field_sort_specs[idx]; }
    auto begin() const noexcept { return _field_sort_specs.begin(); }
    auto end() const noexcept { return _field_sort_specs.end(); }
    const std::vector<FieldSortSpec>& get_field_sort_specs() const noexcept { return _field_sort_specs; }
};

} // namespace search::common
