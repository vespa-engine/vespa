// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_index.h"
#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/searchlib/util/index_stats.h>
#include <vespa/searchcommon/common/schema.h>
#include <string>

namespace search::diskindex {

/**
 * This class represents a disk index that contains a set of field indexes that are independent of each other.
 */
class DiskIndex : public queryeval::Searchable {
    std::string                       _indexDir;
    index::Schema                          _schema;
    std::vector<FieldIndex>                _field_indexes;
    uint32_t                               _nonfield_size_on_disk;
    TuneFileSearch                         _tuneFileSearch;
    std::shared_ptr<IPostingListCache>     _posting_list_cache;

    void calculate_nonfield_size_on_disk();
    bool loadSchema();
    bool openDictionaries(const TuneFileSearch &tuneFileSearch);

public:
    /**
     * Create a view of the disk index located in the given directory.
     *
     * @param indexDir the directory where the disk index is located.
     * @param posting_list_cache cache for posting lists and bitvectors.
     */
    explicit DiskIndex(const std::string &indexDir, std::shared_ptr<IPostingListCache> posting_list_cache);
    ~DiskIndex() override;

    /**
     * Setup this instance by opening and loading relevant index files.
     *
     * @return true if this instance was successfully setup.
     */
    bool setup(const TuneFileSearch &tuneFileSearch);
    bool setup(const TuneFileSearch &tuneFileSearch, const DiskIndex &old);

    /**
     * Perform a dictionary lookup for the given word in the given field.
     *
     * @param indexId the id of the field to perform lookup for.
     * @param word the word to lookup.
     * @return the lookup result or nullptr if the word is not found.
     */
    index::DictionaryLookupResult lookup(uint32_t indexId, std::string_view word);

    std::unique_ptr<queryeval::Blueprint> createBlueprint(const queryeval::IRequestContext & requestContext,
                                                          const queryeval::FieldSpec &field,
                                                          const query::Node &term) override;

    std::unique_ptr<queryeval::Blueprint> createBlueprint(const queryeval::IRequestContext & requestContext,
                                                          const queryeval::FieldSpecList &fields,
                                                          const query::Node &term) override;

    /**
     * Get stats for this index.
     */
    IndexStats get_stats(bool clear_disk_io_stats) const;
    const index::Schema &getSchema() const { return _schema; }
    const std::string &getIndexDir() const { return _indexDir; }

    index::FieldLengthInfo get_field_length_info(const std::string& field_name) const;
    const std::shared_ptr<IPostingListCache>& get_posting_list_cache() const noexcept { return _posting_list_cache; }
    const FieldIndex& get_field_index(uint32_t field_id) const noexcept { return _field_indexes[field_id]; }
};

}
