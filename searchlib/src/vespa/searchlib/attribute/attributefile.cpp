// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefile.h"
#include <vespa/searchlib/util/filesizecalculator.h>
#include <vespa/vespalib/util/error.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/data/fileheader.h>
#include <stdexcept>

#include <vespa/log/log.h>
LOG_SETUP(".attributefile");

using vespalib::IllegalStateException;
using search::common::FileHeaderContext;
using vespalib::getLastErrorString;

namespace search {

using attribute::BasicType;

namespace {

void
updateHeader(const vespalib::string &name)
{
    vespalib::FileHeader h;
    FastOS_File f;
    f.OpenReadWrite(name.c_str());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.rewriteFile(f);
    f.Close();
}

}

ReadAttributeFile::ReadAttributeFile(const vespalib::string & fileName,
                                     const Config & config)
    : AttributeFile(fileName, config)
{
    OpenReadOnly();
    seekIdxPos(0);
}


WriteAttributeFile::WriteAttributeFile(const vespalib::string &fileName,
                                       const Config &config,
                                       const FileHeaderContext &
                                       fileHeaderContext,
                                       uint32_t docIdLimit)
    : AttributeFile(fileName, config)
{
    OpenWriteOnly(fileHeaderContext, docIdLimit);
}


void
AttributeFile::OpenReadOnly()
{
    if ( ! _datFile->OpenReadOnly() ) {
        LOG(error, "could not open %s: %s",
            _datFile->GetFileName(), getLastErrorString().c_str());
        throw IllegalStateException(
                vespalib::make_string(
                        "Failed opening attribute data file '%s' for reading",
                        _datFile->GetFileName()));
    }
    vespalib::FileHeader datHeader;
    _datHeaderLen = datHeader.readFile(*_datFile);
    _datFile->SetPosition(_datHeaderLen);
    _datFileSize = _datFile->GetSize();
    if (!FileSizeCalculator::extractFileSize(datHeader, _datHeaderLen,
                                             _datFile->GetFileName(),
                                             _datFileSize)) {
        LOG_ABORT("should not be reached");
    }
    if (_idxFile.get()) {
        if ( ! _idxFile->OpenReadOnly()) {
            LOG(error, "could not open %s: %s",
                _idxFile->GetFileName(), getLastErrorString().c_str());
            throw IllegalStateException(
                    vespalib::make_string(
                            "Failed opening attribute idx file '%s'"
                            " for reading",
                            _idxFile->GetFileName()));
        }
        vespalib::FileHeader idxHeader;
        _idxHeaderLen = idxHeader.readFile(*_idxFile);
        _idxFile->SetPosition(_idxHeaderLen);
        _idxFileSize = _idxFile->GetSize();
        if (!FileSizeCalculator::extractFileSize(idxHeader, _idxHeaderLen,
                                                 _idxFile->GetFileName(),
                                                 _idxFileSize)) {
            LOG_ABORT("should not be reached");
        }
        if (_weightFile.get()) {
            if ( ! _weightFile->OpenReadOnly()) {
                LOG(error, "could not open %s: %s",
                    _weightFile->GetFileName(), getLastErrorString().c_str());
                throw IllegalStateException(
                        vespalib::make_string(
                                "Failed opening attribute weight file '%s'"
                                " for reading",
                                _weightFile->GetFileName()));
            }
            vespalib::FileHeader weightHeader;
            _weightHeaderLen = weightHeader.readFile(*_weightFile);
            _weightFile->SetPosition(_weightHeaderLen);
        }
    }
}


void
AttributeFile::OpenWriteOnly(const FileHeaderContext &fileHeaderContext,
                             uint32_t docIdLimit)
{
    if ( ! _datFile->OpenWriteOnlyTruncate() ) {
        LOG(error, "could not open %s: %s",
            _datFile->GetFileName(), getLastErrorString().c_str());
        throw IllegalStateException(
                vespalib::make_string(
                        "Failed opening attribute data file '%s' for writing",
                        _datFile->GetFileName()));
    }
    vespalib::FileHeader datHeader;
    typedef vespalib::GenericHeader::Tag Tag;
    fileHeaderContext.addTags(datHeader, _datFile->GetFileName());
    datHeader.putTag(Tag("desc", "Attribute vector data file"));

    datHeader.putTag(Tag("datatype", _config.basicType().asString()));
    datHeader.putTag(Tag("collectiontype",
                         _config.collectionType().asString()));
    datHeader.putTag(Tag("docIdLimit", docIdLimit));
    datHeader.putTag(Tag("frozen", 0));
    _datHeaderLen = datHeader.writeFile(*_datFile);
    if (_idxFile.get()) {
        if ( ! _idxFile->OpenWriteOnlyTruncate()) {
            LOG(error, "could not open %s: %s",
                _idxFile->GetFileName(), getLastErrorString().c_str());
            throw IllegalStateException(
                    vespalib::make_string(
                            "Failed opening attribute idx file '%s'"
                            " for writing",
                            _idxFile->GetFileName()));
        }
        vespalib::FileHeader idxHeader;
        fileHeaderContext.addTags(idxHeader, _idxFile->GetFileName());
        idxHeader.putTag(Tag("desc", "Attribute vector idx file"));
        idxHeader.putTag(Tag("datatype",
                             _config.basicType().asString()));
        idxHeader.putTag(Tag("collectiontype",
                             _config.collectionType().asString()));
        idxHeader.putTag(Tag("docIdLimit", docIdLimit));
        idxHeader.putTag(Tag("frozen", 0));
        _idxHeaderLen = idxHeader.writeFile(*_idxFile);
        if ( ! _idxFile->CheckedWrite(&_currIdx, sizeof(_currIdx))) {
            LOG(error, "could not write to %s: %s",
                _idxFile->GetFileName(), getLastErrorString().c_str());
            throw IllegalStateException(
                    vespalib::make_string(
                            "Failed writing first idx"
                            " to attribute idx file '%s'",
                            _weightFile->GetFileName()));
        }
        if (_weightFile.get()) {
            if ( ! _weightFile->OpenWriteOnlyTruncate()) {
                LOG(error, "could not open %s: %s",
                    _weightFile->GetFileName(), getLastErrorString().c_str());
                throw IllegalStateException(
                        vespalib::make_string(
                                "Failed opening attribute weight file '%s'"
                                " for writing",
                                _weightFile->GetFileName()));
            }
            vespalib::FileHeader weightHeader;
            fileHeaderContext.addTags(weightHeader,
                                      _weightFile->GetFileName());
            weightHeader.putTag(Tag("desc", "Attribute vector weight file"));
            weightHeader.putTag(Tag("datatype",
                                    _config.basicType().asString()));
            weightHeader.putTag(Tag("collectiontype",
                                    _config.collectionType().asString()));
            weightHeader.putTag(Tag("docIdLimit", docIdLimit));
            weightHeader.putTag(Tag("frozen", 0));
            _weightHeaderLen = weightHeader.writeFile(*_weightFile);
        }
    }
}


void
AttributeFile::enableDirectIO()
{
    _datFile->EnableDirectIO();
    if (_idxFile.get()) {
        _idxFile->EnableDirectIO();
        if (_weightFile.get()) {
            _weightFile->EnableDirectIO();
        }
    }
}


void
AttributeFile::Close()
{
    if (_datFile->IsOpened()) {
        bool writeMode = _datFile->IsWriteMode();
        _datFile->Flush();
        _datFile->Close();
        if (writeMode) {
            updateHeader(_datFile->GetFileName());
        }
    }
    if (_idxFile.get() != NULL && _idxFile->IsOpened()) {
        bool writeMode = _idxFile->IsWriteMode();
        _idxFile->Flush();
        _idxFile->Close();
        if (writeMode) {
            updateHeader(_idxFile->GetFileName());
        }
    }
    if (_weightFile.get() != NULL && _weightFile->IsOpened()) {
        bool writeMode = _weightFile->IsWriteMode();
        _weightFile->Flush();
        _weightFile->Close();
        if (writeMode) {
            updateHeader(_weightFile->GetFileName());
        }
    }
}


AttributeFile::AttributeFile(const vespalib::string &fileName,
                             const Config &config)
    : _currIdx(0),
      _datFile(new Fast_BufferedFile( new FastOS_File((fileName + ".dat").c_str()))),
      _idxFile(config.collectionType().isMultiValue() ?
               new Fast_BufferedFile(new FastOS_File((fileName + ".idx").c_str())) :
               NULL),
      _weightFile(config.collectionType().isWeightedSet() ?
                  new Fast_BufferedFile( new FastOS_File((fileName + ".weight").c_str())) :
                  NULL),
      _fileName(fileName),
      _config(config),
      _datHeaderLen(0u),
      _idxHeaderLen(0u),
      _weightHeaderLen(0u),
      _datFileSize(0),
      _idxFileSize(0)
{
}


AttributeFile::~AttributeFile()
{
    Close();
}


bool
AttributeFile::seekIdxPos(size_t idxPos)
{
    bool retval(false);
    if (_idxFile.get()) {
        _idxFile->SetPosition(_idxHeaderLen + idxPos * sizeof(uint32_t));
        retval = (_idxFile->Read(&_currIdx, sizeof(_currIdx)) ==
                  sizeof(_currIdx));
    }
    return retval;
}


bool
AttributeFile::read(Record &record)
{
    bool retval(true);
    uint32_t nextIdx(_currIdx + 1);
    if (_idxFile.get()) {
        if (static_cast<uint64_t>(_idxFile->GetPosition()) >= _idxFileSize) {
            retval = false;
        } else {
            retval = (_idxFile->Read(&nextIdx, sizeof(nextIdx))
                      == sizeof(nextIdx));
            assert(nextIdx >= _currIdx);
        }
    } else {
        if (static_cast<uint64_t>(_datFile->GetPosition()) >= _datFileSize) {
            retval = false;
        }
    }
    if (retval) {
        retval = record.read(*this, nextIdx - _currIdx);
        _currIdx = nextIdx;
    }

    return retval;
}


bool
AttributeFile::write(const Record & record)
{
    bool retval(record.write(*this));
    if (retval && _idxFile.get()) {
        _currIdx += record.getValueCount();
        retval = _idxFile->CheckedWrite(&_currIdx, sizeof(_currIdx));
    }

    return retval;
}


std::unique_ptr<AttributeFile::Record>
AttributeFile::getRecord()
{
    std::unique_ptr<Record> record;
    switch (_config.basicType().type()) {
        case BasicType::UINT1:
        case BasicType::UINT2:
        case BasicType::UINT4:
        case BasicType::INT8:
            record.reset(new FixedRecord<int8_t>());
            break;
        case BasicType::INT16:
            record.reset(new FixedRecord<int16_t>());
            break;
        case BasicType::INT32:
            record.reset(new FixedRecord<int32_t>());
            break;
        case BasicType::INT64:
            record.reset(new FixedRecord<int64_t>());
            break;
        case BasicType::FLOAT:
            record.reset(new FixedRecord<float>());
            break;
        case BasicType::DOUBLE:
            record.reset(new FixedRecord<double>());
            break;
        case BasicType::STRING:
            record.reset(new VariableRecord());
            break;
        default:
            break;
    }
    return record;
}


template <typename T>
bool
AttributeFile::FixedRecord<T>::onWrite(AttributeFile & dest) const
{
    bool retval(dest._datFile->CheckedWrite(&_data[0],
                        _data.size() * sizeof(T)));
    if (retval && dest._weightFile.get()) {
        retval = dest._weightFile->CheckedWrite(&_weight[0],
                _weight.size() * sizeof(int32_t));
    }
    return retval;
}


bool
AttributeFile::VariableRecord::onWrite(AttributeFile & dest) const
{
    bool retval(dest._datFile->CheckedWrite(&_data[0], _data.size()));
    if (retval && dest._weightFile.get()) {
        retval = dest._weightFile->CheckedWrite(&_weight[0],
                _weight.size() * sizeof(int32_t));
    }
    return retval;
}


void
AttributeFile::VariableRecord::setValue(const void * v, size_t len)
{
    _data.resize(len);
    memcpy(&_data[0], v, len);
    _weight.clear();
}


size_t
AttributeFile::VariableRecord::getValueCount() const
{
    size_t numValues(_weight.size());
    if ( numValues == 0) {
        for(size_t i(0), m(_data.size()); i < m; i++) {
            if (_data[i] == 0) {
                numValues++;
            }
        }
    }
    return numValues;
}


template <typename T>
bool
AttributeFile::FixedRecord<T>::onRead(AttributeFile &src, size_t numValues)
{
    bool retval(true);
    _data.resize(numValues);
    if (numValues) {
        const int bytesRead = src._datFile->Read(&_data[0],
                _data.size() * sizeof(T));
        retval = (bytesRead == int(_data.size() * sizeof(T)));
    }
    if (src._weightFile.get()) {
        _weight.resize(numValues);
        if (numValues && retval) {
            const int bytesRead = src._weightFile->Read(&_weight[0],
                    _weight.size() * sizeof(uint32_t));
            retval = (bytesRead == int(_weight.size() * sizeof(uint32_t)));
        }
    }
    return retval;
}


bool
AttributeFile::VariableRecord::onRead(AttributeFile &src, size_t numValues)
{
    bool retval(true);
    _data.resize(0);
    if (numValues) {
        size_t stringsRead(0);
        for (int c; (stringsRead < numValues) &&
                 ((c = src._datFile->GetByte()) >= 0); ) {
             _data.push_back(c);
             if (c == 0) {
                 stringsRead++;
             }
        }
        retval = (stringsRead == numValues);
    }
    if (src._weightFile.get()) {
        _weight.resize(numValues);
        if (numValues && retval) {
            const int bytesRead = src._weightFile->Read(&_weight[0],
                    _weight.size() * sizeof(uint32_t));
            retval = (bytesRead == int(_weight.size() * sizeof(uint32_t)));
        }
    }
    return retval;
}


}
