// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::RecordFlatFile
 * @ingroup allocator
 *
 * @brief Templated class for keeping fixed sized records of primitives on disk.
 *
 * This file is used to keep a number of fixed sized records on disk,
 * it provides an abstraction layer, such that one doesn't have to worry
 * about the disk access.
 *
 * It implements the disk access using FastOS_File and opens the file
 * in combined Read/Write mode if writing is necessary.
 *
 * New entries are appended without checking if they previous exist.
 * Updating entries change them in place. Deleting entries, moves the
 * last entry in the file into the position of the entry that is being
 * deleted, and file is truncated to fit.
 *
 * The class is defined to be a template, to prevent the need for the
 * extra resources consumed by using inheritance.
 *
 * A record implementation should look something like this:
 *
 * class Record {
 * public:
 *     Record(const Record&);
 *
 *     Id getId() const;
 *     Record& operator=(const Record&);
 *     bool isValid();
 * };
 *
 * class Id {
 * public:
 *     operator==(const Id&) const;
 * };
 *
 * ostream& operator<<(ostream& out, const Id&);
 *
 * NB: As records are written directly from memory to disk, and are
 * reconstructed merely by copying disk content back into memory, they
 * cannot include pointers or references as these types of variables would
 * not be correctly saved and restored. It is thus safes to only use
 * primitives.
 *
 * Note that this interface is not threadsafe. The class keeps a memory
 * area for buffering, that is used during both read and write operations.
 * Thus only one operation can be performed at a time.
 *
 * @author H�kon Humberset
 * @date 2005-04-28
 * @version $Id$
 */

#pragma once

#include <cassert>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/fastos/fastos.h>
#include <list>
#include <memory>
#include <stdexcept>
#include <sstream>
#include <string>

namespace storage {

/**
 * Helper class to get a FastOS file that throws exceptions.
 */
class ExceptionThrowingFile {
private:
    FastOS_File _file;

public:
    ExceptionThrowingFile(const std::string& filename);

    void openReadOnly() throw (vespalib::IoException);
    void openWriteOnly() throw (vespalib::IoException);
    void openReadWrite() throw (vespalib::IoException);
    void read(void* _buffer, unsigned int length) throw (vespalib::IoException);
    void write(const void* _buffer, unsigned int length)
        throw (vespalib::IoException);
    void setPosition(int64_t position) throw (vespalib::IoException);
    int64_t getPosition() throw (vespalib::IoException);
    void setSize(int64_t size) throw (vespalib::IoException);
    int64_t getSize() throw (vespalib::IoException);
    void remove() throw (vespalib::IoException);
    bool exists() throw (vespalib::IoException);
};

template<class Record, class Id>
class RecordFlatFile {
private:
    RecordFlatFile(const RecordFlatFile &);
    RecordFlatFile& operator=(const RecordFlatFile &);

    Record *_record; // Cache of a chunk of records
    const std::string _path;
    const unsigned int _chunkSize; // In kilobytes
    const unsigned int _maxChunkRecordCount;
    mutable std::list<std::string> _nonFatalErrors;

public:
    RecordFlatFile(const std::string& path, unsigned int chunksize = 4096)
        throw (vespalib::IllegalArgumentException, vespalib::FatalException,
               vespalib::IoException);
    ~RecordFlatFile();

    bool exists(const Id& id) const throw (vespalib::IoException);
    std::unique_ptr<Record> getRecord(const Id& id) const
        throw (vespalib::IoException);

    bool update(const Record& record) throw (vespalib::IoException);
    void add(const Record& record) throw (vespalib::IoException);
    bool remove(const Id& id) throw (vespalib::IoException);
    void clear() throw (vespalib::IoException);

      // Functions to get entries in the flatfile by index. Used by tests to
      // ensure correct operation.
    unsigned int getSize() const throw (vespalib::IoException);
    std::unique_ptr<Record> operator[](unsigned int index) const
        throw (document::IllegalArgumentException, vespalib::IoException);

    bool errorsFound() const { return (_nonFatalErrors.size() > 0); }
    const std::list<std::string>& getErrors() const { return _nonFatalErrors; }
    void clearErrors() { _nonFatalErrors.clear(); }
};

template<class Record, class Id> RecordFlatFile<Record, Id>::
RecordFlatFile(const std::string& path,unsigned int chunksize)
throw (document::IllegalArgumentException, document::FatalException,
       vespalib::IoException)
    : _record(0),
      _path(path),
      _chunkSize(chunksize * sizeof(Record)),
      _maxChunkRecordCount(chunksize),
      _nonFatalErrors()
{
    if (_maxChunkRecordCount == 0) {
        throw document::IllegalArgumentException(
            "RecordFlatFile("+_path+"): Chunksize given doesn't allow for any "
            "records. Increase chunksize to at least sizeof(Record)", VESPA_STRLOC);
    }
    _record = new Record[chunksize];
    if (!_record) {
        throw document::FatalException(
            "RecordFlatFile("+_path+"): Failed to allocate buffer", VESPA_STRLOC);
    }
      // Make sure file exists
    ExceptionThrowingFile file(_path);
    file.openReadWrite();
}

template<class Record, class Id>
RecordFlatFile<Record, Id>::~RecordFlatFile()
{
    delete[] _record;
}

template<class Record, class Id>
bool RecordFlatFile<Record, Id>::exists(const Id& id) const
throw (vespalib::IoException)
{
    return (getRecord(id).get() != (Record*) 0);
}

template<class Record, class Id> std::unique_ptr<Record>
RecordFlatFile<Record, Id>::getRecord(const Id& id) const
throw (vespalib::IoException)
{
    ExceptionThrowingFile file(_path);
    if (file.exists()) {
        file.openReadOnly();
        unsigned int recordCount = file.getSize() / sizeof(Record);
        unsigned int currentRecord = 0;
        while (currentRecord < recordCount) {
            unsigned int chunkRecordCount =
                std::min(_maxChunkRecordCount, recordCount - currentRecord);
            file.read(_record, chunkRecordCount * sizeof(Record));
            for (unsigned int i=0; i<chunkRecordCount; ++i) {
                if (_record[i].getId() == id) {
                    if (!_record[i].isValid()) {
                        std::ostringstream ost;
                        ost << "Entry requested '" << id << "' is corrupted "
                            << "in file " << _path;
                        throw vespalib::IoException(ost.str(), VESPA_STRLOC);
                    }
                    return std::unique_ptr<Record>(new Record(_record[i]));
                }
                if (!_record[i].isValid()) {
                    _nonFatalErrors.push_back(
                            "Found corrupted entry in file "+_path);
                }
            }
            currentRecord += chunkRecordCount;
        }
    }
    return std::unique_ptr<Record>(0);
}

template<class Record, class Id>
bool RecordFlatFile<Record, Id>::update(const Record& record)
throw (vespalib::IoException)
{
    if (!record.isValid()) {
        std::ostringstream ost;
        ost << "Updating " << _path << " using invalid record '"
            << record.getId() << "'.";
        _nonFatalErrors.push_back(ost.str());
    }
    ExceptionThrowingFile file(_path);
    file.openReadWrite();
    unsigned int recordCount = file.getSize() / sizeof(Record);
    unsigned int currentRecord = 0;
    while (currentRecord < recordCount) {
        unsigned int chunkRecordCount =
            std::min(_maxChunkRecordCount, recordCount - currentRecord);
        file.read(_record, chunkRecordCount * sizeof(Record));
        for (unsigned int i=0; i<chunkRecordCount; ++i) {
            if (_record[i].getId() == record.getId()) {
                _record[i] = record;
                file.setPosition(file.getPosition()
                                 - (chunkRecordCount - i) * sizeof(Record));
                file.write(&_record[i], sizeof(Record));
                return true;
            }
        }
        currentRecord += chunkRecordCount;
    }
    return false;
}

template<class Record, class Id>
void RecordFlatFile<Record, Id>::add(const Record& record)
throw (vespalib::IoException)
{
    if (!record.isValid()) {
        std::ostringstream ost;
        ost << "Adding invalid record '"
            << record.getId() << "' to file " << _path << ".";
        _nonFatalErrors.push_back(ost.str());
    }
    ExceptionThrowingFile file(_path);
    file.openWriteOnly();
    file.setPosition(file.getSize());
    file.write(&record, sizeof(Record));
}

template<class Record, class Id>
bool RecordFlatFile<Record, Id>::remove(const Id& id)
throw (vespalib::IoException)
{
    ExceptionThrowingFile file(_path);
    file.openReadWrite();
    int64_t fileSize = file.getSize();
    if (fileSize == 0) return false;
    Record last;
    {  // Read the last entry
        file.setPosition(file.getSize() - sizeof(Record));
        file.read(&last, sizeof(Record));
        if (!last.isValid()) {
            _nonFatalErrors.push_back(
                    "Last entry in file "+_path+" is invalid");
        }
        if (last.getId() == id) {
            file.setSize(file.getSize() - sizeof(Record));
            return true;
        }
        file.setPosition(0);
    }

    unsigned int recordCount = file.getSize() / sizeof(Record);
    unsigned int currentRecord = 0;
    while (currentRecord < recordCount) {
        unsigned int chunkRecordCount =
            std::min(_maxChunkRecordCount, recordCount - currentRecord);
        file.read(_record, chunkRecordCount * sizeof(Record));
        for (unsigned int i=0; i<chunkRecordCount; ++i) {
            if (_record[i].getId() == id) {
                _record[i] = last;
                file.setPosition(file.getPosition()
                                 - (chunkRecordCount - i) * sizeof(Record));
                file.write(&_record[i], sizeof(Record));
                file.setSize(file.getSize() - sizeof(Record));
                return true;
            }
            if (!_record[i].isValid()) {
                _nonFatalErrors.push_back(
                        "Found corrupted entry in file "+_path);
            }
        }
        currentRecord += chunkRecordCount;
    }
    return false;
}

template<class Record, class Id>
void RecordFlatFile<Record, Id>::clear()
throw (vespalib::IoException)
{
    ExceptionThrowingFile file(_path);
    file.remove();
}

template<class Record, class Id>
unsigned int RecordFlatFile<Record, Id>::getSize() const
throw (vespalib::IoException)
{
    ExceptionThrowingFile file(_path);
    file.openReadOnly();
    int64_t fileSize = file.getSize();
    if (fileSize % sizeof(Record) != 0) {
        _nonFatalErrors.push_back(
                "Filesize is not a whole number of records. "
                "File "+_path+" corrupted or wrong size gotten.");
    }
    return static_cast<unsigned int>(fileSize / sizeof(Record));
}

template<class Record, class Id>
std::unique_ptr<Record> RecordFlatFile<Record, Id>::
operator[](unsigned int index) const
throw (document::IllegalArgumentException, vespalib::IoException)
{
    ExceptionThrowingFile file(_path);
    file.openReadOnly();
    unsigned int recordCount = file.getSize() / sizeof(Record);
    if (index >= recordCount) {
        throw document::IllegalArgumentException(
            "RecordFlatFile.operator[]: Access outside of bounds", VESPA_STRLOC);
    }
    file.setPosition(index * sizeof(Record));
    file.read(_record, sizeof(Record));
    return std::unique_ptr<Record>(new Record(_record[0]));
}

}

