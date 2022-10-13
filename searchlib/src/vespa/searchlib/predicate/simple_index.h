// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "common.h"
#include <vespa/vespalib/btree/btreestore.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <optional>

namespace search::predicate {

template <typename Key = uint64_t, typename DocId = uint32_t>
struct SimpleIndexDeserializeObserver {
    virtual ~SimpleIndexDeserializeObserver() {}
    virtual void notifyInsert(Key key, DocId docId, uint32_t k) = 0;
};

template <typename Posting>
struct PostingSerializer {
    virtual ~PostingSerializer() {}
    virtual void serialize(const Posting &posting, vespalib::DataBuffer &buffer) const = 0;
};

template <typename Posting>
struct PostingDeserializer {
    virtual ~PostingDeserializer() {}
    virtual Posting deserialize(vespalib::DataBuffer &buffer) = 0;
};

struct SimpleIndexConfig {
    static constexpr double DEFAULT_UPPER_DOCID_FREQ_THRESHOLD = 0.40;
    static constexpr double DEFAULT_LOWER_DOCID_FREQ_THRESHOLD = 0.8 * DEFAULT_UPPER_DOCID_FREQ_THRESHOLD;
    static constexpr size_t DEFAULT_UPPER_VECTOR_SIZE_THRESHOLD = 10000;
    static constexpr size_t DEFAULT_LOWER_VECTOR_SIZE_THRESHOLD = static_cast<size_t>(0.8 * DEFAULT_UPPER_VECTOR_SIZE_THRESHOLD);
    static constexpr size_t DEFAULT_VECTOR_PRUNE_FREQUENCY = 20000;
    static constexpr double DEFAULT_FOREACH_VECTOR_THRESHOLD = 0.25;

    // Create vector posting list if doc frequency is above
    double upper_docid_freq_threshold = DEFAULT_UPPER_DOCID_FREQ_THRESHOLD;
    // Remove vector posting list if doc frequency is below
    double lower_docid_freq_threshold = DEFAULT_LOWER_DOCID_FREQ_THRESHOLD;
    // Threshold to create vector posting list
    size_t upper_vector_size_threshold = DEFAULT_UPPER_VECTOR_SIZE_THRESHOLD;
    // Threshold to remove vector posting list
    size_t lower_vector_size_threshold = DEFAULT_LOWER_VECTOR_SIZE_THRESHOLD;
    // How often to prune vector when add is called
    size_t vector_prune_frequency = DEFAULT_VECTOR_PRUNE_FREQUENCY;
    // Use vector posting list in foreach_frozen if doc frequency is above
    double foreach_vector_threshold = DEFAULT_FOREACH_VECTOR_THRESHOLD;
    // Grow strategy for the posting vectors
    vespalib::GrowStrategy grow_strategy = vespalib::GrowStrategy();

    SimpleIndexConfig() {}
    SimpleIndexConfig(double upper_docid_freq_threshold_,
                      double lower_docid_freq_threshold_,
                      size_t upper_vector_size_threshold_,
                      size_t lower_vector_size_threshold_,
                      size_t vector_prune_frequency_,
                      double foreach_vector_threshold_,
                      vespalib::GrowStrategy grow_strategy_)
            : upper_docid_freq_threshold(upper_docid_freq_threshold_),
              lower_docid_freq_threshold(lower_docid_freq_threshold_),
              upper_vector_size_threshold(upper_vector_size_threshold_),
              lower_vector_size_threshold(lower_vector_size_threshold_),
              vector_prune_frequency(vector_prune_frequency_),
              foreach_vector_threshold(foreach_vector_threshold_),
              grow_strategy(grow_strategy_) {}
    SimpleIndexConfig(double upper_docid_freq_threshold_, vespalib::GrowStrategy grow_strategy_)
            : upper_docid_freq_threshold(upper_docid_freq_threshold_),
              lower_docid_freq_threshold(upper_docid_freq_threshold_ * 0.80),
              grow_strategy(grow_strategy_) {}
};

template <typename Posting, typename Key, typename DocId>
class PostingVectorIterator {
    using PostingVector = vespalib::RcuVectorBase<Posting>;

    const Posting * const _vector;
    const size_t _size;
    size_t _pos;
    Posting _data;

public:
    // Handle both move and copy construction
    PostingVectorIterator(PostingVectorIterator&&) = default;
    PostingVectorIterator& operator=(PostingVectorIterator&&) = default;
    PostingVectorIterator(const PostingVectorIterator&) = default;
    PostingVectorIterator& operator=(const PostingVectorIterator&) = default;

    explicit PostingVectorIterator(const PostingVector & vector, size_t size)
        : _vector(&vector.acquire_elem_ref(0)),
          _size(size)
    {
        assert(_size <= vector.get_size()); // Data race: not writer
        linearSeek(1);
    }

    bool valid() const { return _pos < _size; }
    DocId getKey() const { return _pos; }
    Posting getData() const { return _data; }
    void linearSeek(DocId doc_id) {
        while (doc_id < _size) {
            const Posting &p = _vector[doc_id];
            if (p.valid()) {
                _pos = doc_id;
                _data = p;
                return;
            }
            ++doc_id;
        }
        _pos = _size;
    }
    PostingVectorIterator & operator++() {
        linearSeek(_pos + 1);
        return *this;
    }
};

/**
 * SimpleIndex holds a dictionary of Keys and posting lists of DocIds
 * with Posting information.
 *
 * Serialization / deserialization assumes that Key fits in 64 bits
 * and DocId fits in 32 bits.
 */
template <typename Posting,
          typename Key = uint64_t, typename DocId = uint32_t>
class SimpleIndex {
public:
    using Dictionary = vespalib::btree::BTree<Key, vespalib::datastore::EntryRef, vespalib::btree::NoAggregated>;
    using DictionaryIterator = typename Dictionary::ConstIterator;
    using BTreeStore = vespalib::btree::BTreeStore<
            DocId, Posting, vespalib::btree::NoAggregated, std::less<DocId>, vespalib::btree::BTreeDefaultTraits>;
    using BTreeIterator = typename BTreeStore::ConstIterator;
    using PostingVector = vespalib::RcuVectorBase<Posting>;
    using VectorStore = vespalib::btree::BTree<Key, std::shared_ptr<PostingVector>, vespalib::btree::NoAggregated>;
    using VectorIterator = PostingVectorIterator<Posting, Key, DocId>;

private:
    using GenerationHolder = vespalib::GenerationHolder;
    using generation_t = vespalib::GenerationHandler::generation_t;
    template <typename T>
    using optional = std::optional<T>;

    Dictionary                _dictionary;
    BTreeStore                _btree_posting_lists;
    VectorStore               _vector_posting_lists;
    GenerationHolder         &_generation_holder;
    uint32_t                  _insert_remove_counter = 0;
    const SimpleIndexConfig   _config;
    const DocIdLimitProvider &_limit_provider;

    void insertIntoPosting(vespalib::datastore::EntryRef &ref, Key key, DocId doc_id, const Posting &posting);
    void insertIntoVectorPosting(vespalib::datastore::EntryRef ref, Key key, DocId doc_id, const Posting &posting);
    void removeFromVectorPostingList(vespalib::datastore::EntryRef ref, Key key, DocId doc_id);
    void pruneBelowThresholdVectors();
    void createVectorIfOverThreshold(vespalib::datastore::EntryRef ref, Key key);
    bool removeVectorIfBelowThreshold(vespalib::datastore::EntryRef ref, typename VectorStore::Iterator &it);

    void logVector(const char *action, Key key, size_t document_count,
                   double ratio, size_t vector_length) const;
    double getDocumentRatio(size_t document_count, uint32_t doc_id_limit) const;
    size_t getDocumentCount(vespalib::datastore::EntryRef ref) const;
    bool shouldCreateVectorPosting(size_t size, double ratio) const;
    bool shouldRemoveVectorPosting(size_t size, double ratio) const;
    size_t getVectorPostingSize(const PostingVector &vector) const {
        return std::min(vector.get_size() /* Data race: not writer */,
                        static_cast<size_t>(_limit_provider.getCommittedDocIdLimit()));
    }

public:
    SimpleIndex(GenerationHolder &generation_holder,
                const DocIdLimitProvider &provider, const SimpleIndexConfig &config)
        : _generation_holder(generation_holder), _config(config), _limit_provider(provider) {}
    ~SimpleIndex();

    void serialize(vespalib::DataBuffer &buffer,
                   const PostingSerializer<Posting> &serializer) const;
    void deserialize(vespalib::DataBuffer &buffer,
                     PostingDeserializer<Posting> &deserializer,
                     SimpleIndexDeserializeObserver<Key, DocId> &observer, uint32_t version);

    void addPosting(Key key, DocId doc_id, const Posting &posting);
    std::pair<Posting, bool> removeFromPostingList(Key key, DocId doc_id);
    // Call promoteOverThresholdVectors() after deserializing a SimpleIndex
    // (and after doc id limits values are determined) to promote posting lists to vectors.
    void promoteOverThresholdVectors();
    void commit();
    void reclaim_memory(generation_t oldest_used_gen);
    void assign_generation(generation_t current_gen);
    vespalib::MemoryUsage getMemoryUsage() const;
    template <typename FunctionType>
    void foreach_frozen_key(vespalib::datastore::EntryRef ref, Key key, FunctionType func) const;

    DictionaryIterator lookup(Key key) const {
        return _dictionary.getFrozenView().find(key);
    }

    size_t getPostingListSize(vespalib::datastore::EntryRef ref) const {
        return _btree_posting_lists.frozenSize(ref);
    }

    BTreeIterator getBTreePostingList(vespalib::datastore::EntryRef ref) const {
        return _btree_posting_lists.beginFrozen(ref);
    }

    optional<VectorIterator> getVectorPostingList(Key key) const {
        auto it = _vector_posting_lists.getFrozenView().find(key);
        if (it.valid()) {
            auto &vector = *it.getData();
            size_t size = getVectorPostingSize(vector);
            return optional<VectorIterator>(VectorIterator(vector, size));
        }
        return optional<VectorIterator>();

    }
};

template<typename Posting, typename Key, typename DocId>
template<typename FunctionType>
void
SimpleIndex<Posting, Key, DocId>::foreach_frozen_key(vespalib::datastore::EntryRef ref, Key key, FunctionType func) const {
    auto it = _vector_posting_lists.getFrozenView().find(key);
    double ratio = getDocumentRatio(getDocumentCount(ref), _limit_provider.getDocIdLimit());
    if (it.valid() && ratio > _config.foreach_vector_threshold) {
        auto &vector = *it.getData();
        size_t size = getVectorPostingSize(vector);
        for (DocId doc_id = 1; doc_id < size; ++doc_id) {
            if (vector[doc_id].valid()) {
                func(doc_id);
            }
        }
    } else {
        _btree_posting_lists.foreach_frozen_key(ref, func);
    }
}

}
