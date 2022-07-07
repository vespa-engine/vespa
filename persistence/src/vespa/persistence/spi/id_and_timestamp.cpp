// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "id_and_timestamp.h"

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

}
