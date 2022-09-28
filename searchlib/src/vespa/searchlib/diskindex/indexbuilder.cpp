// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexbuilder.h"
#include "fieldwriter.h"
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/field_length_info.h>
#include <vespa/searchlib/index/i_field_length_inspector.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/common/documentsummary.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/error.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.indexbuilder");


namespace search::diskindex {

namespace {

using common::FileHeaderContext;
using index::DocIdAndFeatures;
using index::FieldLengthInfo;
using index::IFieldLengthInspector;
using index::PostingListCounts;
using index::Schema;
using index::SchemaUtil;
using index::WordDocElementFeatures;
using index::schema::DataType;
using vespalib::getLastErrorString;

class FileHandle {
private:
    std::shared_ptr<FieldWriter> _fieldWriter;

public:
    FileHandle();
    ~FileHandle();

    void open(vespalib::stringref dir,
              const SchemaUtil::IndexIterator &index,
              uint32_t docIdLimit, uint64_t numWordIds,
              const FieldLengthInfo &field_length_info,
              const TuneFileSeqWrite &tuneFileWrite,
              const FileHeaderContext &fileHeaderContext);

    void close();

    FieldWriter* writer() { return _fieldWriter.get(); }
};

}

class IndexBuilder::FieldHandle {
private:
    bool _valid;
    const Schema *_schema; // Ptr to allow being std::vector member
    uint32_t _fieldId;
    IndexBuilder *_builder; // Ptr to allow being std::vector member
    FileHandle _file;

public:
    FieldHandle(const Schema &schema,
                uint32_t fieldId,
                IndexBuilder *builder);

    ~FieldHandle();

    void new_word(vespalib::stringref word);
    void add_document(const index::DocIdAndFeatures &features);

    const Schema::IndexField &getSchemaField();
    const vespalib::string &getName();
    vespalib::string getDir();
    void open(uint32_t docIdLimit, uint64_t numWordIds,
              const FieldLengthInfo &field_length_info,
              const TuneFileSeqWrite &tuneFileWrite,
              const FileHeaderContext &fileHeaderContext);
    void close();

    void setValid() { _valid = true; }
    bool getValid() const { return _valid; }
    uint32_t getIndexId() const { return _fieldId; }
};


FileHandle::FileHandle()
    : _fieldWriter()
{
}

FileHandle::~FileHandle() = default;

void
FileHandle::open(vespalib::stringref dir,
                 const SchemaUtil::IndexIterator &index,
                 uint32_t docIdLimit, uint64_t numWordIds,
                 const FieldLengthInfo &field_length_info,
                 const TuneFileSeqWrite &tuneFileWrite,
                 const FileHeaderContext &fileHeaderContext)
{
    assert(_fieldWriter.get() == nullptr);

    _fieldWriter = std::make_shared<FieldWriter>(docIdLimit, numWordIds);

    if (!_fieldWriter->open(dir + "/", 64, 262144u, false,
                            index.use_interleaved_features(),
                            index.getSchema(), index.getIndex(),
                            field_length_info,
                            tuneFileWrite, fileHeaderContext)) {
        LOG(error, "Could not open term writer %s for write (%s)",
            vespalib::string(dir).c_str(), getLastErrorString().c_str());
        LOG_ABORT("should not be reached");
    }
}

void
FileHandle::close()
{
    bool ret = true;
    if (_fieldWriter != nullptr) {
        bool closeRes = _fieldWriter->close();
        _fieldWriter.reset();
        if (!closeRes) {
            LOG(error,
                "Could not close field writer");
            ret = false;
        }
    }
    assert(ret);
    (void) ret;
}

IndexBuilder::FieldHandle::FieldHandle(const Schema &schema,
                                       uint32_t fieldId,
                                       IndexBuilder *builder)
    : _valid(false),
      _schema(&schema),
      _fieldId(fieldId),
      _builder(builder),
      _file()
{
}

IndexBuilder::FieldHandle::~FieldHandle() = default;

void
IndexBuilder::FieldHandle::new_word(vespalib::stringref word)
{
    assert(_valid);
    _file.writer()->newWord(word);
}

void
IndexBuilder::FieldHandle::add_document(const index::DocIdAndFeatures &features)
{
    _file.writer()->add(features);
}

const Schema::IndexField &
IndexBuilder::FieldHandle::getSchemaField()
{
    return _schema->getIndexField(_fieldId);
}

const vespalib::string &
IndexBuilder::FieldHandle::getName()
{
    return getSchemaField().getName();
}

vespalib::string
IndexBuilder::FieldHandle::getDir()
{
    return _builder->appendToPrefix(getName());
}

void
IndexBuilder::FieldHandle::open(uint32_t docIdLimit, uint64_t numWordIds,
                                const FieldLengthInfo &field_length_info,
                                const TuneFileSeqWrite &tuneFileWrite,
                                const FileHeaderContext &fileHeaderContext)
{
    _file.open(getDir(),
               SchemaUtil::IndexIterator(*_schema, getIndexId()),
               docIdLimit, numWordIds,
               field_length_info,
               tuneFileWrite, fileHeaderContext);
}

void
IndexBuilder::FieldHandle::close()
{
    _file.close();
}

IndexBuilder::IndexBuilder(const Schema &schema)
    : index::IndexBuilder(schema),
      _currentField(nullptr),
      _curDocId(noDocId()),
      _lowestOKDocId(1u),
      _curWord(),
      _inWord(false),
      _lowestOKFieldId(0u),
      _fields(),
      _prefix(),
      _docIdLimit(0u),
      _numWordIds(0u),
      _schema(schema)
{
    // TODO: Filter for text indexes
    for (uint32_t i = 0, ie = schema.getNumIndexFields(); i < ie; ++i) {
        const Schema::IndexField &iField = schema.getIndexField(i);
        FieldHandle fh(schema, i, this);
        // Only know how to handle string index for now.
        if (iField.getDataType() == DataType::STRING) {
            fh.setValid();
        }
        _fields.push_back(fh);
    }
}

IndexBuilder::~IndexBuilder() = default;

void
IndexBuilder::startField(uint32_t fieldId)
{
    assert(_curDocId == noDocId());
    assert(_currentField == nullptr);
    assert(fieldId < _fields.size());
    assert(fieldId >= _lowestOKFieldId);
    _currentField = &_fields[fieldId];
    assert(_currentField != nullptr);
}

void
IndexBuilder::endField()
{
    assert(_curDocId == noDocId());
    assert(!_inWord);
    assert(_currentField != nullptr);
    _lowestOKFieldId = _currentField->getIndexId() + 1;
    _currentField = nullptr;
}

void
IndexBuilder::startWord(vespalib::stringref word)
{
    assert(_currentField != nullptr);
    assert(!_inWord);
    // TODO: Check sort order
    _curWord = word;
    _inWord = true;
    _currentField->new_word(word);
}

void
IndexBuilder::endWord()
{
    assert(_inWord);
    assert(_currentField != nullptr);
    _inWord = false;
    _lowestOKDocId = 1u;
}

void
IndexBuilder::add_document(const index::DocIdAndFeatures &features)
{
    assert(_inWord);
    assert(_currentField != nullptr);
    _currentField->add_document(features);
}

void
IndexBuilder::setPrefix(vespalib::stringref prefix)
{
    _prefix = prefix;
}

vespalib::string
IndexBuilder::appendToPrefix(vespalib::stringref name)
{
    if (_prefix.empty()) {
        return name;
    }
    return _prefix + "/" + name;
}

void
IndexBuilder::open(uint32_t docIdLimit, uint64_t numWordIds,
                   const IFieldLengthInspector &field_length_inspector,
                   const TuneFileIndexing &tuneFileIndexing,
                   const FileHeaderContext &fileHeaderContext)
{
    std::vector<uint32_t> indexes;

    _docIdLimit = docIdLimit;
    _numWordIds = numWordIds;
    if (!_prefix.empty()) {
        std::filesystem::create_directory(std::filesystem::path(_prefix));
    }
    // TODO: Filter for text indexes
    for (FieldHandle & fh : _fields) {
        if (!fh.getValid()) {
            continue;
        }
        std::filesystem::create_directory(std::filesystem::path(fh.getDir()));
        fh.open(docIdLimit, numWordIds,
                field_length_inspector.get_field_length_info(fh.getName()),
                tuneFileIndexing._write,
                 fileHeaderContext);
        indexes.push_back(fh.getIndexId());
    }
    vespalib::string schemaFile = appendToPrefix("schema.txt");
    if (!_schema.saveToFile(schemaFile)) {
        LOG(error, "Cannot save schema to \"%s\"", schemaFile.c_str());
        LOG_ABORT("should not be reached");
    }
}

void
IndexBuilder::close()
{
    // TODO: Filter for text indexes
    for (FieldHandle & fh : _fields) {
        if (fh.getValid()) {
            fh.close();
            vespalib::File::sync(fh.getDir());
        }
    }
    if (!docsummary::DocumentSummary::writeDocIdLimit(_prefix, _docIdLimit)) {
        LOG(error, "Could not write docsum count in dir %s: %s",
            _prefix.c_str(), getLastErrorString().c_str());
        LOG_ABORT("should not be reached");
    }
}

}
