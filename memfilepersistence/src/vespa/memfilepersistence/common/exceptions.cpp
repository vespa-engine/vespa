// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exceptions.h"

namespace storage {
namespace memfile {

VESPA_IMPLEMENT_EXCEPTION_SPINE(TimestampExistException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(InconsistentSlotException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(MemFileIoException);
VESPA_IMPLEMENT_EXCEPTION(NoDisksException, vespalib::Exception);

VESPA_IMPLEMENT_MEMFILE_EXCEPTION(SlotNotFoundException);
VESPA_IMPLEMENT_MEMFILE_EXCEPTION(InvalidArgumentException);
VESPA_IMPLEMENT_MEMFILE_EXCEPTION(InvalidStateException);
VESPA_IMPLEMENT_MEMFILE_EXCEPTION(CorruptMemFileException);
VESPA_IMPLEMENT_MEMFILE_EXCEPTION(MemFileWrapperException);
VESPA_IMPLEMENT_MEMFILE_EXCEPTION(InconsistentException);

MemFileException::MemFileException(const FileSpecification& file)
    : _file(file)
{
}

MemFileException::~MemFileException()
{
}

TimestampExistException::TimestampExistException(
        const vespalib::string& message, const FileSpecification& file,
        Types::Timestamp ts, const vespalib::string& location, int skipStack)
    : Exception(message, location, skipStack + 1),
      MemFileException(file),
      _timestamp(ts)
{
}

InconsistentSlotException::InconsistentSlotException(
        const vespalib::string& message, const FileSpecification& file,
        const MemSlot& slot, const vespalib::string& location, int skipstack)
    : InconsistentException(message, file, location, skipstack + 1),
      _slot(slot)
{
}

MemFileIoException::MemFileIoException(
        const vespalib::string& msg, const FileSpecification& file,
        Type type, const vespalib::string& location, int skipStack)
    : IoException(msg, type, location, skipStack + 1),
      MemFileException(file)
{
}

} // memfile
} // storage
