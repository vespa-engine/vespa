// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <concepts>
#include <format>
#include <functional>
#include <map>
#include <string>
#include <variant>
#include <vector>

namespace search::queryeval::test {

using FieldValueT = std::variant<int64_t, double, bool, std::string>;

/**
 * Holds one typed value and evaluates predicates against it.
 */
class Field {
    FieldValueT _value;

public:
    Field(auto value) : _value(value) {}

    [[nodiscard]] bool check(auto&& predicate) const {
        return std::visit(
            [&](auto&& val) noexcept {
                if constexpr (std::invocable<decltype(predicate), decltype(val)>) {
                    return predicate(val);
                } else {
                    return false;
                }
            },
            _value);
    }

    std::string to_string() const {
        return std::visit(
            [](const auto& val) -> std::string {
                if constexpr (std::same_as<decltype(val), std::string>) {
                    return std::format("'{}'", val);
                } else if constexpr (std::same_as<decltype(val), bool>) {
                    return val ? "true" : "false";
                } else {
                    return std::format("{}", val);
                }
            },
            _value);
    }
};

using Pred = std::function<bool(const Field&)>;

/**
 * Holds a set of named fields representing one test record.
 */
class Record {
    std::map<std::string, Field> _fields;

public:
    const std::map<std::string, Field>& data() const { return _fields; }

    Record& set(const std::string& name, const auto& value) {
        _fields.insert_or_assign(name, Field{value});
        return *this;
    }

    [[nodiscard]] bool check(const std::string& name, const Pred& pred) const {
        if (auto it = _fields.find(name); it != _fields.end()) {
            return pred(it->second);
        }
        return false;
    }

    std::string to_string() const {
        std::stringstream ss;
        ss << "Record{";
        bool first = true;
        for (const auto& [name, field] : _fields) {
            if (!first) {
                ss << ", ";
            }
            ss << name << ": " << field.to_string();
            first = false;
        }
        ss << "}";
        return ss.str();
    }
};

/**
 * Holds predicates that all must match for a record to pass.
 */
class Filter {
    std::vector<std::pair<std::string, Pred>> _predicates;

public:
    Filter& lt(const std::string& name, auto value) {
        _predicates.emplace_back(name, [value](const Field& field) {
            return field.check([value](decltype(value) val) { return val < value; });
        });
        return *this;
    }

    Filter& eq(const std::string& name, auto value) {
        _predicates.emplace_back(name, [value](const Field& field) {
            return field.check([value](decltype(value) val) { return val == value; });
        });
        return *this;
    }

    [[nodiscard]] bool check(const Record& rec) const {
        for (const auto& [name, pred] : _predicates) {
            if (!rec.check(name, pred)) {
                return false;
            }
        }
        return true;
    }
};

/**
 * Holds records.
 */
class DataPond {
    std::vector<Record> _records;

public:
    const std::vector<Record>& records() { return _records; }

    Record& new_record() {
        _records.emplace_back();
        return _records.back();
    }

    void add(const Record& record) { _records.push_back(record); }
};

} // namespace search::queryeval::test
