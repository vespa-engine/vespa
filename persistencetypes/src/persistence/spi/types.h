// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <memory>

namespace vespalib {
    class nbostream;
}

namespace document {
    class GlobalId;
    class Document;
    class DocumentId;
    class DocumentUpdate;
}

/**
 * We create small wrapper classes for number values for the following reasons:
 *   - Being able to create functions taking in several of them, without risking
 *     caller using numbers in wrong order.
 *   - We can identify type by typename instead of variable name.
 */
#define DEFINE_PRIMITIVE_WRAPPER(type, name) \
  class name { \
      type _value; \
  public: \
      typedef type Type; \
      name() noexcept : _value() {} \
      explicit name(type v) noexcept : _value(v) {} \
      operator type() const noexcept { return _value; } \
      operator type&() noexcept { return _value; } \
      type getValue() const noexcept { return _value; } \
      name& operator=(type val) noexcept { _value = val; return *this; } \
      friend vespalib::nbostream & \
      operator<<(vespalib::nbostream &os, const name &wrapped); \
      friend vespalib::nbostream & \
      operator>>(vespalib::nbostream &is, name &wrapped); \
  }; \

#define DEFINE_PRIMITIVE_WRAPPER_NBOSTREAM(name) \
  vespalib::nbostream & \
  operator<<(vespalib::nbostream &os, const name &wrapped) \
  { \
      os << wrapped._value; \
      return os; \
  } \
  \
  vespalib::nbostream & \
  operator>>(vespalib::nbostream &is, name &wrapped) \
  { \
      is >> wrapped._value; \
      return is; \
  } \

namespace storage::spi {

/**
 * \class storage::spi::NodeIndex
 * \ingroup spi
 */
DEFINE_PRIMITIVE_WRAPPER(uint16_t, NodeIndex);

/**
 * \class storage::spi::IteratorId
 * \ingroup spi
 */
DEFINE_PRIMITIVE_WRAPPER(uint64_t, IteratorId);

/**
 * \class storage::spi::Timestamp
 * \ingroup spi
 */
DEFINE_PRIMITIVE_WRAPPER(uint64_t, Timestamp);

/**
 * \class storage::spi::BucketChecksum
 * \ingroup spi
 */
DEFINE_PRIMITIVE_WRAPPER(uint32_t, BucketChecksum);

// Import critical dependencies into SPI namespace. This makes interface look
// cleaner, and makes it easy to exchange actual implementation.
using Document = document::Document;
using DocumentUpdate = document::DocumentUpdate;
using DocumentId = document::DocumentId;
using GlobalId = document::GlobalId;
using TimestampList = std::vector<Timestamp>;
using string = vespalib::string;
using DocumentUP = std::unique_ptr<document::Document>;
using DocumentIdUP = std::unique_ptr<document::DocumentId>;
using DocumentSP = std::shared_ptr<document::Document>;
using DocumentUpdateSP = std::shared_ptr<document::DocumentUpdate>;

enum IncludedVersions {
    NEWEST_DOCUMENT_ONLY,
    NEWEST_DOCUMENT_OR_REMOVE,
    ALL_VERSIONS
};

enum MaintenanceLevel {
    LOW,
    HIGH
};

}
