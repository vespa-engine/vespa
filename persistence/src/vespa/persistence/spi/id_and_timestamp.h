// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "types.h"
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/stllike/string.h>
#include <iosfwd>

namespace vespalib { class asciistream; }

namespace storage::spi {

/**
 * Convenience wrapper for referencing a document ID at a particular timestamp.
 *
 * Prefer this instead of a std::pair due to named fields and a pre-provided hash function.
 */
struct IdAndTimestamp {
    document::DocumentId id;
    Timestamp timestamp;

    IdAndTimestamp();
    IdAndTimestamp(document::DocumentId id_, Timestamp timestamp_) noexcept;

    IdAndTimestamp(const IdAndTimestamp&);
    IdAndTimestamp& operator=(const IdAndTimestamp&);
    IdAndTimestamp(IdAndTimestamp&&) noexcept;
    IdAndTimestamp& operator=(IdAndTimestamp&&) noexcept;

    bool operator==(const IdAndTimestamp& rhs) const noexcept {
        return ((id == rhs.id) && (timestamp == rhs.timestamp));
    }

    void print(vespalib::asciistream&) const;
    vespalib::string to_string() const;

    struct hash {
        size_t operator()(const IdAndTimestamp& id_ts) const noexcept {
            const size_t h = document::GlobalId::hash()(id_ts.id.getGlobalId());
            return h ^ (id_ts.timestamp + 0x9e3779b9U + (h << 6U) + (h >> 2U)); // Basically boost::hash_combine
        }
    };
};

vespalib::asciistream& operator<<(vespalib::asciistream&, const IdAndTimestamp&);
std::ostream& operator<<(std::ostream&, const IdAndTimestamp&);

}
