#pragma once

#include <vespa/searchlib/query/string_range_spec.h>

#include <memory>
#include <string>

namespace vespalib {
class asciistream;
}

namespace search::query {

class StringRange {
    std::unique_ptr<StringRangeSpec> _spec;

public:
    StringRange(std::unique_ptr<StringRangeSpec> spec) noexcept : _spec(std::move(spec)) {}
    StringRange(const StringRange& other);
    StringRange(StringRange&& other) noexcept = default;
    StringRange& operator=(const StringRange& other);
    StringRange& operator=(StringRange&& other) noexcept = default;
    ~StringRange();

    const StringRangeSpec* get_spec() const { return _spec.get(); }
};

bool operator==(const StringRange& r1, const StringRange& r2);

vespalib::asciistream& operator<<(vespalib::asciistream& out, const StringRange& string_range);

} // namespace search::query
