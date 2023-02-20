// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/indexbuilder.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <limits>
#include <vector>

namespace search::common { class FileHeaderContext; }
namespace search::index { class IFieldLengthInspector; }

namespace search::diskindex {

class BitVectorCandidate;

/**
 * Class used to build a disk index for the set of index fields specified in a schema.
 *
 * The resulting disk index consists of field indexes that are independent of each other.
 */
class IndexBuilder : public index::IndexBuilder {
public:
    // Schema argument must live until IndexBuilder has been deleted.
    IndexBuilder(const index::Schema &schema, vespalib::stringref prefix, uint32_t docIdLimit);
    ~IndexBuilder() override;

    void startField(uint32_t fieldId) override;
    void endField() override;
    void startWord(vespalib::stringref word) override;
    void endWord() override;
    void add_document(const index::DocIdAndFeatures &features) override;
    vespalib::string appendToPrefix(vespalib::stringref name) const;

    void open(uint64_t numWordIds, const index::IFieldLengthInspector &field_length_inspector,
              const TuneFileIndexing &tuneFileIndexing,
              const common::FileHeaderContext &fileHandleContext);

    void close();
private:
    class FieldHandle;
    const index::Schema      &_schema;
    std::vector<FieldHandle>  _fields;
    const vespalib::string    _prefix;
    vespalib::string          _curWord;
    const uint32_t            _docIdLimit;
    int32_t                   _curFieldId;
    uint32_t                  _lowestOKFieldId;
    uint32_t                  _curDocId;
    bool                      _inWord;

    static std::vector<IndexBuilder::FieldHandle> extractFields(const index::Schema &schema, IndexBuilder & builder);

    static uint32_t noDocId() {
        return std::numeric_limits<uint32_t>::max();
    }

    static uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }
    FieldHandle & currentField();
};

}
