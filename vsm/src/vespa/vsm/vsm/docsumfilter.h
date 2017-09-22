// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vsm/common/docsum.h>
#include <vespa/vsm/common/fieldmodifier.h>
#include <vespa/vsm/vsm/docsumfieldspec.h>
#include <vespa/vsm/vsm/fieldsearchspec.h>
#include <vespa/vsm/vsm/flattendocsumwriter.h>
#include <vespa/vsm/vsm/vsm-adapter.h>
#include <vespa/searchsummary/docsummary/resultpacker.h>
#include <vespa/searchsummary/docsummary/docsumstore.h>

using search::docsummary::IDocsumStore;
using search::docsummary::DocsumStoreValue;
using search::docsummary::ResType;
using search::docsummary::ResultPacker;

namespace vsm {

/**
 * This class implements the IDocsumStore interface such that docsum blobs
 * can be fetched based on local document id. The docsum blobs are generated
 * on the fly when requested.
 **/
class DocsumFilter : public IDocsumStore
{
private:
    typedef std::vector<DocsumFieldSpec>   FieldSpecList; // list of summary field specs
    typedef std::vector<vespalib::string>       StringList;
    typedef StringFieldIdTMap              FieldMap;

    const IDocSumCache  * _docsumCache;
    DocsumToolsPtr        _tools;
    FieldSpecList         _fields;        // list of summary fields to generate
    size_t                _highestFieldNo;
    ResultPacker          _packer;
    FlattenDocsumWriter   _flattenWriter;
    const FieldModifierMap * _snippetModifiers;
    document::FieldValue::UP _cachedValue;
    document::FieldPath _emptyFieldPath;

    DocsumFilter(const DocsumFilter &);
    DocsumFilter &operator=(const DocsumFilter &);
    void prepareFieldSpec(DocsumFieldSpec & spec, const DocsumTools::FieldSpec & toolsSpec,
                          const FieldMap & fieldMap, const FieldPathMapT & fieldPathMap);
    const document::FieldValue * getFieldValue(const DocsumFieldSpec::FieldIdentifier & fieldId,
                                               VsmsummaryConfig::Fieldmap::Command command,
                                               const Document & docsum, bool & modified);
    void writeField(const document::FieldValue & fv, const FieldPath & path, ResType type, ResultPacker & packer);
    void writeSlimeField(const DocsumFieldSpec & fieldSpec, const Document & docsum, ResultPacker & packer);
    void writeFlattenField(const DocsumFieldSpec & fieldSpec, const Document & docsum, ResultPacker & packer);
    void writeEmpty(ResType type, ResultPacker & packer);

public:
    DocsumFilter(const DocsumToolsPtr & tools, const IDocSumCache & docsumCache);
    virtual ~DocsumFilter();
    const DocsumToolsPtr & getTools() const { return _tools; }

    /**
     * Initializes this docsum filter using the given field map and field path map.
     * The field map is used to map from field name to field id.
     * The field path map is used to retrieve the field path for each input field.
     *
     * @param fieldMap maps from field name -> field id
     * @param fieldPathMap maps from field id -> field path
     **/
    void init(const FieldMap & fieldMap, const FieldPathMapT & fieldPathMap);

    /**
     * Sets the snippet modifiers to use when writing string fields used as input to snippet generation.
     **/
    void setSnippetModifiers(const FieldModifierMap & modifiers) { _snippetModifiers = &modifiers; }

    /**
     * Returns the highest field id + 1 among all fields in the field spec list.
     **/
    size_t getHighestFieldNo() const { return _highestFieldNo; }


    void setDocSumStore(const IDocSumCache & docsumCache) { _docsumCache = &docsumCache; }

    // Inherit doc from IDocsumStore
    DocsumStoreValue getMappedDocsum(uint32_t id) override;
    uint32_t getNumDocs() const override;
    uint32_t getSummaryClassId() const override;
};

}

