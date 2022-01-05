// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "result.h"
#include "docentry.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace storage::spi {

Result::Result(const Result &) = default;
Result::Result(Result&&) noexcept = default;
Result & Result::operator = (const Result &) = default;
Result& Result::operator=(Result&&) noexcept = default;
Result::~Result() = default;

vespalib::string
Result::toString() const {
    vespalib::asciistream os;
    os << "Result(" << static_cast<int>(_errorCode) << ", " << _errorMessage << ")";
    return os.str();
}

std::ostream &
operator << (std::ostream & os, const Result & r) {
    return os << r.toString();
}

std::ostream & operator << (std::ostream & os, const Result::ErrorType &errorCode) {
    return os << static_cast<int>(errorCode);
}

GetResult::GetResult(Document::UP doc, Timestamp timestamp)
    : Result(),
      _timestamp(timestamp),
      _doc(std::move(doc)),
      _is_tombstone(false)
{
}

GetResult::GetResult(Timestamp removed_at_ts, bool is_tombstone)
    : _timestamp(removed_at_ts),
      _doc(),
      _is_tombstone(is_tombstone)
{
}

GetResult::~GetResult() = default;
BucketIdListResult::~BucketIdListResult() = default;

IterateResult::~IterateResult() = default;
IterateResult::IterateResult(IterateResult &&) noexcept = default;
IterateResult & IterateResult::operator=(IterateResult &&) noexcept = default;

IterateResult::IterateResult(ErrorType error, const vespalib::string& errorMessage)
    : Result(error, errorMessage),
      _completed(false)
{ }


IterateResult::IterateResult(List entries, bool completed)
    : _completed(completed),
      _entries(std::move(entries))
{ }

IterateResult::List
IterateResult::steal_entries() {
    return std::move(_entries);
}

}

