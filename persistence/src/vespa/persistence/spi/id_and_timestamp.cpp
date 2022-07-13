// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "id_and_timestamp.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage::spi {

IdAndTimestamp::IdAndTimestamp() : id(), timestamp(0) {}
IdAndTimestamp::IdAndTimestamp(document::DocumentId id_, Timestamp timestamp_) noexcept
    : id(std::move(id_)),
      timestamp(timestamp_)
{}

IdAndTimestamp::IdAndTimestamp(const IdAndTimestamp&) = default;
IdAndTimestamp& IdAndTimestamp::operator=(const IdAndTimestamp&) = default;
IdAndTimestamp::IdAndTimestamp(IdAndTimestamp&&) noexcept = default;
IdAndTimestamp& IdAndTimestamp::operator=(IdAndTimestamp&&) noexcept = default;

void IdAndTimestamp::print(vespalib::asciistream& os) const {
    os << id.toString() << " at time " << timestamp.getValue();
}

vespalib::string IdAndTimestamp::to_string() const {
    vespalib::asciistream os;
    print(os);
    return os.str();
}

vespalib::asciistream& operator<<(vespalib::asciistream& os, const IdAndTimestamp& id_ts) {
    id_ts.print(os);
    return os;
}
std::ostream& operator<<(std::ostream& os, const IdAndTimestamp& id_ts) {
    os << id_ts.to_string();
    return os;
}

}
