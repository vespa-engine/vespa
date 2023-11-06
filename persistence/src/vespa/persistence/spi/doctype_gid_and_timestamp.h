// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "types.h"
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/string.h>
#include <iosfwd>

namespace vespalib { class asciistream; }

namespace storage::spi {

/**
 * Convenience wrapper for referencing a document type and global id with
 * a timestamp.
 *
 * Prefer this instead of a std::tuple due to named fields and a pre-provided hash function.
 */
struct DocTypeGidAndTimestamp {
    vespalib::string doc_type;
    document::GlobalId gid;
    Timestamp timestamp;

    DocTypeGidAndTimestamp();
    DocTypeGidAndTimestamp(const vespalib::string& doc_type_, document::GlobalId gid_, Timestamp timestamp_) noexcept;

    DocTypeGidAndTimestamp(const DocTypeGidAndTimestamp&);
    DocTypeGidAndTimestamp& operator=(const DocTypeGidAndTimestamp&);
    DocTypeGidAndTimestamp(DocTypeGidAndTimestamp&&) noexcept;
    DocTypeGidAndTimestamp& operator=(DocTypeGidAndTimestamp&&) noexcept;

    bool operator==(const DocTypeGidAndTimestamp& rhs) const noexcept {
        return ((doc_type == rhs.doc_type) && (gid == rhs.gid) &&
                (timestamp == rhs.timestamp));
    }

    void print(vespalib::asciistream&) const;
    vespalib::string to_string() const;

    struct hash {
        size_t operator()(const DocTypeGidAndTimestamp& dt_gid_ts) const noexcept {
            size_t h = document::GlobalId::hash()(dt_gid_ts.gid);
            h = h ^ (vespalib::hash<vespalib::string>()(dt_gid_ts.doc_type) + 0x9e3779b9U + (h << 6U) + (h >> 2U));
            return h ^ (dt_gid_ts.timestamp + 0x9e3779b9U + (h << 6U) + (h >> 2U)); // Basically boost::hash_combine
        }
    };
};

vespalib::asciistream& operator<<(vespalib::asciistream&, const DocTypeGidAndTimestamp&);
std::ostream& operator<<(std::ostream&, const DocTypeGidAndTimestamp&);

}
