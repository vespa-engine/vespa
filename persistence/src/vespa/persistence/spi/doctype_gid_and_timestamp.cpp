// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "doctype_gid_and_timestamp.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage::spi {

DocTypeGidAndTimestamp::DocTypeGidAndTimestamp()
    : doc_type(),
      gid()
{
}

DocTypeGidAndTimestamp::DocTypeGidAndTimestamp(const vespalib::string& doc_type_, document::GlobalId gid_, Timestamp timestamp_) noexcept
    : doc_type(doc_type_),
      gid(std::move(gid_)),
      timestamp(timestamp_)
{}

DocTypeGidAndTimestamp::DocTypeGidAndTimestamp(const DocTypeGidAndTimestamp&) = default;
DocTypeGidAndTimestamp& DocTypeGidAndTimestamp::operator=(const DocTypeGidAndTimestamp&) = default;
DocTypeGidAndTimestamp::DocTypeGidAndTimestamp(DocTypeGidAndTimestamp&&) noexcept = default;
DocTypeGidAndTimestamp& DocTypeGidAndTimestamp::operator=(DocTypeGidAndTimestamp&&) noexcept = default;

void DocTypeGidAndTimestamp::print(vespalib::asciistream& os) const {
    os << doc_type << ":" << gid.toString() << " at time " << timestamp.getValue();
}

vespalib::string DocTypeGidAndTimestamp::to_string() const {
    vespalib::asciistream os;
    print(os);
    return os.str();
}

vespalib::asciistream& operator<<(vespalib::asciistream& os, const DocTypeGidAndTimestamp& dt_gid_ts) {
    dt_gid_ts.print(os);
    return os;
}
std::ostream& operator<<(std::ostream& os, const DocTypeGidAndTimestamp& dt_gid_ts) {
    os << dt_gid_ts.to_string();
    return os;
}

}
