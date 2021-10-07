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
    class FieldHandle;

    using Schema = index::Schema;
private:
    // Text fields
    FieldHandle             *_currentField;
    uint32_t                 _curDocId;
    uint32_t                 _lowestOKDocId;
    vespalib::string         _curWord;
    bool                     _inWord;
    uint32_t                 _lowestOKFieldId;
    std::vector<FieldHandle> _fields;   // Defined fields.
    vespalib::string         _prefix;
    uint32_t                 _docIdLimit;
    uint64_t                 _numWordIds;

    const Schema &_schema;

    static uint32_t noDocId() {
        return std::numeric_limits<uint32_t>::max();
    }

    static uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }

public:
    typedef index::WordDocElementWordPosFeatures WordDocElementWordPosFeatures;

    // Schema argument must live until IndexBuilder has been deleted.
    IndexBuilder(const Schema &schema); 
    ~IndexBuilder() override;

    void startField(uint32_t fieldId) override;
    void endField() override;
    void startWord(vespalib::stringref word) override;
    void endWord() override;
    void add_document(const index::DocIdAndFeatures &features) override;

    void setPrefix(vespalib::stringref prefix);

    vespalib::string appendToPrefix(vespalib::stringref name);

    void open(uint32_t docIdLimit, uint64_t numWordIds,
              const index::IFieldLengthInspector &field_length_inspector,
              const TuneFileIndexing &tuneFileIndexing,
              const common::FileHeaderContext &fileHandleContext);

    void close();
};

}
