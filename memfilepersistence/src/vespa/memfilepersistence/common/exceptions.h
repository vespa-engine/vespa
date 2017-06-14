// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::IoException
 * \ingroup memfile
 *
 * \brief Exception thrown by memfile layer for IO problems.
 *
 * Storage needs to know what disk was having issues for disk related problems,
 * in case it needs to disable a non-working disk. Some information on what
 * file was being operated on while one is having trouble is nice anyhow. Thus
 * specific exceptions have been created to keep the file specification of the
 * file in question. The MemFile layer may throw some exceptions that aren't
 * MemFileExceptions though. These exceptions should not be disk/file related.
 */

#pragma once

#include "filespecification.h"
#include <vespa/memfilepersistence/memfile/memslot.h>
#include <vespa/vespalib/util/exceptions.h>

#define VESPA_DEFINE_MEMFILE_EXCEPTION(name) \
    struct name : public vespalib::Exception, public MemFileException { \
    name(const vespalib::string& message, const FileSpecification& file, \
         const vespalib::string& location, int skipStack = 0); \
    name(const name &); \
    name & operator = (const name &); \
    name(name &&) = default; \
    name & operator = (name &&) = default; \
    ~name(); \
    VESPA_DEFINE_EXCEPTION_SPINE(name); \
};

#define VESPA_IMPLEMENT_MEMFILE_EXCEPTION(name) \
    name::name(const vespalib::string& message, const FileSpecification& file, \
         const vespalib::string& location, int skipStack) \
        : vespalib::Exception(message, location, skipStack + 1), \
          MemFileException(file) {} \
    name::name(const name &) = default; \
    name & name::operator = (const name &) = default; \
    name::~name() {} \
    VESPA_IMPLEMENT_EXCEPTION_SPINE(name);

namespace storage::memfile {

VESPA_DEFINE_EXCEPTION(NoDisksException, vespalib::Exception);

/**
 * \class storage::memfile::MemFileException
 * \ingroup memfile
 *
 * \brief Interface to implement for exceptions that contain a file specification specifying what memfile was problematic.
 */
class MemFileException : protected Types {
    FileSpecification _file;

public:
    MemFileException(const FileSpecification&);
    virtual ~MemFileException() = 0;

    const FileSpecification& getFile() const { return _file; }
};

VESPA_DEFINE_MEMFILE_EXCEPTION(SlotNotFoundException);
VESPA_DEFINE_MEMFILE_EXCEPTION(InvalidArgumentException);
VESPA_DEFINE_MEMFILE_EXCEPTION(InvalidStateException);
VESPA_DEFINE_MEMFILE_EXCEPTION(CorruptMemFileException);
VESPA_DEFINE_MEMFILE_EXCEPTION(MemFileWrapperException);

/**
 * \class storage::InconsistentException
 * \ingroup memfile
 *
 * \brief Thrown by MemFile::verifyConsistent() if inconsistent
 */
VESPA_DEFINE_MEMFILE_EXCEPTION(InconsistentException);

/**
 * @class storage::TimestampExistException
 * @ingroup filestorage
 *
 * @brief Thrown by SlotFile::write() when timestamp given is already in use.
 */
class TimestampExistException : public vespalib::Exception,
                                public MemFileException
{
    Timestamp _timestamp;
public:
    TimestampExistException(const vespalib::string& message,
                            const FileSpecification&, Timestamp ts,
                            const vespalib::string& location, int skipstack = 0);
    TimestampExistException(const TimestampExistException &);
    ~TimestampExistException();

    VESPA_DEFINE_EXCEPTION_SPINE(TimestampExistException);

    Timestamp getTimestamp() const { return _timestamp; }
};

/**
 * @class storage::InconsistentSlotException
 * @ingroup filestorage
 *
 * @brief Thrown by MemFile::verifyConsistent() if a slot is inconsistent
 */
class InconsistentSlotException : public InconsistentException {
    MemSlot _slot;

public:
    InconsistentSlotException(const vespalib::string& message,
                              const FileSpecification&, const MemSlot& slot,
                              const vespalib::string& location, int skipstack = 0);
    InconsistentSlotException(const InconsistentSlotException &);
    ~InconsistentSlotException();

    VESPA_DEFINE_EXCEPTION_SPINE(InconsistentSlotException);
};

class MemFileIoException : public vespalib::IoException,
                           public MemFileException
{
public:
    MemFileIoException(const vespalib::string& msg, const FileSpecification&,
                       Type type, const vespalib::string& location,
                       int skipStack = 0);
    MemFileIoException(const MemFileIoException &);
    ~MemFileIoException();

    VESPA_DEFINE_EXCEPTION_SPINE(MemFileIoException);
};

} // memfile
