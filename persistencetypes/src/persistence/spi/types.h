// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/document.h>
#include <vespa/document/base/globalid.h>

namespace vespalib
{

class nbostream;

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
      name() : _value() {} \
      explicit name(type v) : _value(v) {} \
      operator type() const { return _value; } \
      operator type&() { return _value; } \
      type getValue() const { return _value; } \
      name& operator=(type val) { _value = val; return *this; } \
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

namespace storage {
namespace spi {

/**
 * \class storage::spi::NodeIndex
 * \ingroup spi
 */
DEFINE_PRIMITIVE_WRAPPER(uint16_t, NodeIndex);

/**
 * \class storage::spi::PartitionId
 * \ingroup spi
 */
DEFINE_PRIMITIVE_WRAPPER(uint16_t, PartitionId);

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
typedef document::Document Document;
typedef document::DocumentUpdate DocumentUpdate;
typedef document::DocumentId DocumentId;
typedef document::GlobalId GlobalId;
typedef std::vector<Timestamp> TimestampList;
typedef vespalib::string string;

enum IncludedVersions {
    NEWEST_DOCUMENT_ONLY,
    NEWEST_DOCUMENT_OR_REMOVE,
    ALL_VERSIONS
};

enum MaintenanceLevel {
    LOW,
    HIGH
};

} // spi
} // storage

