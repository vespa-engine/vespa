// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexbuilder.h"
#include "fieldwriter.h"
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/field_length_info.h>
#include <vespa/searchlib/index/i_field_length_inspector.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/common/documentsummary.h>
#include <vespa/vespalib/io/fileutil.h>
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
    const Schema   &_schema;
    IndexBuilder   &_builder;
    FileHandle      _file;
    const uint32_t  _fieldId;
    const bool      _valid;
public:
    FieldHandle(const Schema &schema, uint32_t fieldId, IndexBuilder & builder, bool valid) noexcept;
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

    _fieldWriter = std::make_shared<FieldWriter>(docIdLimit, numWordIds, dir + "/");

    if (!_fieldWriter->open(64, 262144u, false,
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
            LOG(error, "Could not close field writer");
            ret = false;
        }
    }
    assert(ret);
    (void) ret;
}

IndexBuilder::FieldHandle::FieldHandle(const Schema &schema, uint32_t fieldId, IndexBuilder &builder, bool valid) noexcept
    : _schema(schema),
      _builder(builder),
      _file(),
      _fieldId(fieldId),
      _valid(valid)
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
    return _schema.getIndexField(_fieldId);
}

const vespalib::string &
IndexBuilder::FieldHandle::getName()
{
    return getSchemaField().getName();
}

vespalib::string
IndexBuilder::FieldHandle::getDir()
{
    return _builder.appendToPrefix(getName());
}

void
IndexBuilder::FieldHandle::open(uint32_t docIdLimit, uint64_t numWordIds,
                                const FieldLengthInfo &field_length_info,
                                const TuneFileSeqWrite &tuneFileWrite,
                                const FileHeaderContext &fileHeaderContext)
{
    _file.open(getDir(), SchemaUtil::IndexIterator(_schema, getIndexId()),
               docIdLimit, numWordIds, field_length_info, tuneFileWrite, fileHeaderContext);
}

void
IndexBuilder::FieldHandle::close()
{
    _file.close();
}

std::vector<IndexBuilder::FieldHandle>
IndexBuilder::extractFields(const Schema &schema, IndexBuilder & builder) {
    std::vector<IndexBuilder::FieldHandle> fields;
    fields.reserve(schema.getNumIndexFields());
    // TODO: Filter for text indexes
    for (uint32_t i = 0; i < schema.getNumIndexFields(); ++i) {
        const Schema::IndexField &iField = schema.getIndexField(i);
        // Only know how to handle string index for now.
        bool valid = (iField.getDataType() == DataType::STRING);
        fields.emplace_back(schema, i, builder, valid);
    }
    return fields;
}

IndexBuilder::IndexBuilder(const Schema &schema, vespalib::stringref prefix, uint32_t docIdLimit)
    : index::IndexBuilder(schema),
      _schema(schema),
      _fields(extractFields(schema, *this)),
      _prefix(prefix),
      _curWord(),
      _docIdLimit(docIdLimit),
      _curFieldId(-1),
      _lowestOKFieldId(0u),
      _curDocId(noDocId()),
      _inWord(false)
{
}

IndexBuilder::~IndexBuilder() = default;

IndexBuilder::FieldHandle &
IndexBuilder::currentField() {
    assert(_curFieldId >= 0);
    assert(_curFieldId < int32_t(_fields.size()));
    return _fields[_curFieldId];
}
void
IndexBuilder::startField(uint32_t fieldId)
{
    assert(_curDocId == noDocId());
    assert(_curFieldId == -1);
    assert(fieldId < _fields.size());
    assert(fieldId >= _lowestOKFieldId);
    _curFieldId = fieldId;
}

void
IndexBuilder::endField()
{
    assert(_curDocId == noDocId());
    assert(!_inWord);
    _lowestOKFieldId = currentField().getIndexId() + 1;
    _curFieldId = -1;
}

void
IndexBuilder::startWord(vespalib::stringref word)
{
    assert(!_inWord);
    // TODO: Check sort order
    _curWord = word;
    _inWord = true;
    currentField().new_word(word);
}

void
IndexBuilder::endWord()
{
    assert(_inWord);
    assert(_curFieldId != -1);
    _inWord = false;
}

void
IndexBuilder::add_document(const index::DocIdAndFeatures &features)
{
    assert(_inWord);
    currentField().add_document(features);
}

vespalib::string
IndexBuilder::appendToPrefix(vespalib::stringref name) const
{
    if (_prefix.empty()) {
        return name;
    }
    return _prefix + "/" + name;
}

void
IndexBuilder::open(uint64_t numWordIds,
                   const IFieldLengthInspector &field_length_inspector,
                   const TuneFileIndexing &tuneFileIndexing,
                   const FileHeaderContext &fileHeaderContext)
{
    std::vector<uint32_t> indexes;

    if (!_prefix.empty()) {
        std::filesystem::create_directory(std::filesystem::path(_prefix));
    }
    // TODO: Filter for text indexes
    for (FieldHandle & fh : _fields) {
        if (!fh.getValid()) {
            continue;
        }
        std::filesystem::create_directory(std::filesystem::path(fh.getDir()));
        fh.open(_docIdLimit, numWordIds,
                field_length_inspector.get_field_length_info(fh.getName()),
                tuneFileIndexing._write, fileHeaderContext);
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
