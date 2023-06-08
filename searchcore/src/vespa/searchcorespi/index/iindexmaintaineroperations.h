// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idiskindex.h"
#include "imemoryindex.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/diskindex/docidmapper.h>
#include <vespa/searchlib/index/i_field_length_inspector.h>

namespace search { class IFlushToken; }

namespace searchcorespi::index {

/**
 * Interface for operations needed by an index maintainer.
 */
struct IIndexMaintainerOperations {
    using IFieldLengthInspector = search::index::IFieldLengthInspector;
    using Schema = search::index::Schema;
    using SelectorArray = search::diskindex::SelectorArray;
    virtual ~IIndexMaintainerOperations() = default;

    /**
     * Creates a new memory index using the given schema.
     */
    virtual IMemoryIndex::SP createMemoryIndex(const Schema& schema,
                                               const IFieldLengthInspector& inspector,
                                               search::SerialNum serialNum) = 0;

    /**
     * Loads a disk index from the given directory.
     */
    virtual IDiskIndex::SP loadDiskIndex(const vespalib::string &indexDir) = 0;

    /**
     * Reloads the given disk index and returns a new instance.
     */
    virtual IDiskIndex::SP reloadDiskIndex(const IDiskIndex &oldIndex) = 0;

    /**
     * Runs fusion on a given set of input disk indexes to create a fusioned output disk index.
     * The selector array contains a source for all local document ids ([0, docIdLimit>)
     * in the range [0, sources.size()> and is used to determine in which input disk index
     * a document is located.
     *
     * @param schema the schema of the resulting fusioned disk index.
     * @param outputDir the output directory of the fusioned disk index.
     * @param sources the directories of the input disk indexes.
     * @param selectorArray the array specifying in which input disk index a document is located.
     * @param lastSerialNum the serial number of the last operation in the last input disk index.
     */
    virtual bool runFusion(const Schema &schema,
                           const vespalib::string &outputDir,
                           const std::vector<vespalib::string> &sources,
                           const SelectorArray &selectorArray,
                           search::SerialNum lastSerialNum,
                           std::shared_ptr<search::IFlushToken> flush_token) = 0;
};

}


