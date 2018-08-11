// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/indexbuilder.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <limits>
#include <vector>

namespace search {

namespace common { class FileHeaderContext; }

namespace diskindex {

class BitVectorCandidate;

class IndexBuilder : public index::IndexBuilder
{
public:
    class FieldHandle;

    typedef index::Schema Schema;
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

    const Schema &_schema;  // Ptr to allow being std::vector member

    static uint32_t noDocId() {
        return std::numeric_limits<uint32_t>::max();
    }

    static uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }

public:
    typedef index::WordDocElementWordPosFeatures WordDocElementWordPosFeatures;

    // schema argument must live until indexbuilder has been deleted.
    IndexBuilder(const Schema &schema); 
    virtual ~IndexBuilder();

    virtual void startWord(vespalib::stringref word) override;
    virtual void endWord() override;
    virtual void startDocument(uint32_t docId) override;
    virtual void endDocument() override;
    virtual void startField(uint32_t fieldId) override;
    virtual void endField() override;
    virtual void startElement(uint32_t elementId, int32_t weight, uint32_t elementLen) override;
    virtual void endElement() override;
    virtual void addOcc(const WordDocElementWordPosFeatures &features) override;

    // TODO: methods for attribute vectors.

    // TODO: methods for document summary.
    inline FieldHandle & getIndexFieldHandle(uint32_t fieldId); 
    void setPrefix(vespalib::stringref prefix);

    vespalib::string appendToPrefix(vespalib::stringref name);

    void
    open(uint32_t docIdLimit, uint64_t numWordIds,
         const TuneFileIndexing &tuneFileIndexing,
         const search::common::FileHeaderContext &fileHandleContext);

    void close();
};

} // namespace diskindex

} // namespace search



