// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    void open(std::string_view dir,
              const SchemaUtil::IndexIterator &index,
              uint32_t docIdLimit, uint64_t numWordIds,
              const FieldLengthInfo &field_length_info,
              const TuneFileSeqWrite &tuneFileWrite,
              const FileHeaderContext &fileHeaderContext);

    void close();

    FieldWriter* writer() { return _fieldWriter.get(); }
};

class FieldHandle {
private:
    const Schema   &_schema;
    IndexBuilder   &_builder;
    FileHandle      _file;
    const uint32_t  _fieldId;
public:
    FieldHandle(const Schema &schema, uint32_t fieldId, IndexBuilder & builder, uint32_t docIdLimit,
                uint64_t numWordIds, const IFieldLengthInspector & field_length_inspector,
                const TuneFileSeqWrite &tuneFileWrite, const FileHeaderContext &fileHeaderContext);
    ~FieldHandle();

    void new_word(std::string_view word);
    void add_document(const index::DocIdAndFeatures &features);

    const Schema::IndexField &getSchemaField();
    const vespalib::string &getName();
    vespalib::string getDir();
    void close();
    uint32_t getIndexId() const noexcept { return _fieldId; }
};

class FieldIndexBuilder : public index::FieldIndexBuilder {
public:
    FieldIndexBuilder(const Schema &schema, uint32_t fieldId, IndexBuilder & builder, uint32_t docidLimit,
                      uint64_t numWordIds, const IFieldLengthInspector & field_length_inspector,
                      const TuneFileSeqWrite &tuneFileWrite, const FileHeaderContext &fileHeaderContext);
    ~FieldIndexBuilder() override;
    void startWord(std::string_view word) override;
    void endWord() override;
    void add_document(const DocIdAndFeatures &features) override;
private:
    FieldHandle          _field;
    vespalib::string     _curWord;
    uint32_t             _curDocId;
    bool                 _inWord;

    static constexpr uint32_t noDocId() {
        return std::numeric_limits<uint32_t>::max();
    }

    static constexpr uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }
};

FieldIndexBuilder::FieldIndexBuilder(const Schema &schema, uint32_t fieldId, IndexBuilder & builder, uint32_t docidLimit,
                                     uint64_t numWordIds, const IFieldLengthInspector & field_length_inspector,
                                     const TuneFileSeqWrite &tuneFileWrite, const FileHeaderContext &fileHeaderContext)
    : _field(schema, fieldId, builder, docidLimit, numWordIds, field_length_inspector, tuneFileWrite, fileHeaderContext),
      _curWord(),
      _curDocId(noDocId()),
      _inWord(false)
{}

FieldIndexBuilder::~FieldIndexBuilder() = default;

void
FieldIndexBuilder::startWord(std::string_view word)
{
    assert(!_inWord);
    // TODO: Check sort order
    _curWord = word;
    _inWord = true;
    _field.new_word(word);
}

void
FieldIndexBuilder::endWord()
{
    assert(_inWord);
    _inWord = false;
}

void
FieldIndexBuilder::add_document(const index::DocIdAndFeatures &features)
{
    assert(_inWord);
    _field.add_document(features);
}

FileHandle::FileHandle()
    : _fieldWriter()
{
}

FileHandle::~FileHandle() = default;

void
FileHandle::open(std::string_view dir,
                 const SchemaUtil::IndexIterator &index,
                 uint32_t docIdLimit, uint64_t numWordIds,
                 const FieldLengthInfo &field_length_info,
                 const TuneFileSeqWrite &tuneFileWrite,
                 const FileHeaderContext &fileHeaderContext)
{
    assert( ! _fieldWriter);

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

FieldHandle::FieldHandle(const Schema &schema, uint32_t fieldId, IndexBuilder &builder, uint32_t docIdLimit,
                         uint64_t numWordIds, const IFieldLengthInspector & field_length_inspector,
                         const TuneFileSeqWrite &tuneFileWrite, const FileHeaderContext &fileHeaderContext)
    : _schema(schema),
      _builder(builder),
      _file(),
      _fieldId(fieldId)
{
    std::filesystem::create_directory(std::filesystem::path(getDir()));
    _file.open(getDir(), SchemaUtil::IndexIterator(_schema, getIndexId()), docIdLimit, numWordIds,
               field_length_inspector.get_field_length_info(getName()), tuneFileWrite, fileHeaderContext);
}

FieldHandle::~FieldHandle() {
    close();
}

void
FieldHandle::new_word(std::string_view word)
{
    _file.writer()->newWord(word);
}

void
FieldHandle::add_document(const index::DocIdAndFeatures &features)
{
    _file.writer()->add(features);
}

const Schema::IndexField &
FieldHandle::getSchemaField()
{
    return _schema.getIndexField(_fieldId);
}

const vespalib::string &
FieldHandle::getName()
{
    return getSchemaField().getName();
}

vespalib::string
FieldHandle::getDir()
{
    return _builder.appendToPrefix(getName());
}

void
FieldHandle::close()
{
    _file.close();
    vespalib::File::sync(getDir());
}

}

std::vector<int32_t>
extractFields(const Schema &schema) {
    std::vector<int32_t> fields;
    fields.reserve(schema.getNumIndexFields());
    // TODO: Filter for text indexes
    for (uint32_t i = 0; i < schema.getNumIndexFields(); ++i) {
        const Schema::IndexField &iField = schema.getIndexField(i);
        // Only know how to handle string index for now.
        bool valid = (iField.getDataType() == DataType::STRING);
        fields.push_back( valid ? i : -1);
    }
    return fields;
}

IndexBuilder::IndexBuilder(const Schema &schema, std::string_view prefix, uint32_t docIdLimit,
                           uint64_t numWordIds, const index::IFieldLengthInspector &field_length_inspector,
                           const TuneFileIndexing &tuneFileIndexing, const search::common::FileHeaderContext &fileHeaderContext)
    : index::IndexBuilder(schema),
      _fields(extractFields(schema)),
      _prefix(prefix),
      _docIdLimit(docIdLimit),
      _numWordIds(numWordIds),
      _field_length_inspector(field_length_inspector),
      _tuneFileIndexing(tuneFileIndexing),
      _fileHeaderContext(fileHeaderContext)
{
    if (!_prefix.empty()) {
        std::filesystem::create_directory(std::filesystem::path(_prefix));
    }
    vespalib::string schemaFile = appendToPrefix("schema.txt");
    if (!_schema.saveToFile(schemaFile)) {
        LOG(error, "Cannot save schema to \"%s\"", schemaFile.c_str());
        LOG_ABORT("should not be reached");
    }
}

IndexBuilder::~IndexBuilder() {
    if (!docsummary::DocumentSummary::writeDocIdLimit(_prefix, _docIdLimit)) {
        LOG(error, "Could not write docsum count in dir %s: %s",
            _prefix.c_str(), getLastErrorString().c_str());
        LOG_ABORT("should not be reached");
    }
}

std::unique_ptr<index::FieldIndexBuilder>
IndexBuilder::startField(uint32_t fieldId) {
    if (_fields[fieldId] >= 0) {
        return std::make_unique<FieldIndexBuilder>(_schema, fieldId, *this, _docIdLimit, _numWordIds,
                                                   _field_length_inspector, _tuneFileIndexing._write,
                                                   _fileHeaderContext);
    }
    return {};
}

vespalib::string
IndexBuilder::appendToPrefix(std::string_view name) const
{
    if (_prefix.empty()) {
        return name;
    }
    return _prefix + "/" + name;
}

}
