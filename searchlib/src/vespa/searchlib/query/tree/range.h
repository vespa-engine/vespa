#pragma once

#include <vespa/searchlib/query/numeric_range_spec.h>
#include <memory>
#include <string>

namespace vespalib { class asciistream; }

namespace search::query {

class Range {
    std::unique_ptr<NumericRangeSpec> _spec;

public:
    Range() noexcept : _spec() {}
    Range(int64_t f, int64_t t);
    Range(std::string range);
    Range(std::unique_ptr<NumericRangeSpec> spec) noexcept : _spec(std::move(spec)) {}
    Range(const Range& other);
    Range(Range&& other) noexcept = default;
    Range& operator=(const Range& other);
    Range& operator=(Range&& other) noexcept = default;
    ~Range();

    const NumericRangeSpec* getSpec() const { return _spec.get(); }
    std::string getRangeString() const;
};

bool operator==(const Range &r1, const Range &r2);

vespalib::asciistream &operator<<(vespalib::asciistream &out, const Range &range);

}
