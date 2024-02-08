// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    IndexBuilder(const index::Schema &schema, vespalib::stringref prefix, uint32_t docIdLimit,
                 uint64_t numWordIds, const index::IFieldLengthInspector &field_length_inspector,
                 const TuneFileIndexing &tuneFileIndexing, const search::common::FileHeaderContext &fileHeaderContext);
    ~IndexBuilder() override;

    std::unique_ptr<index::FieldIndexBuilder> startField(uint32_t fieldId) override;
    vespalib::string appendToPrefix(vespalib::stringref name) const;
private:
    std::vector<int>          _fields;
    const vespalib::string    _prefix;
    const uint32_t            _docIdLimit;
    const uint32_t            _numWordIds;
    const index::IFieldLengthInspector       &_field_length_inspector;
    const TuneFileIndexing                   &_tuneFileIndexing;
    const search::common::FileHeaderContext  &_fileHeaderContext;

    static uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }
};

}
