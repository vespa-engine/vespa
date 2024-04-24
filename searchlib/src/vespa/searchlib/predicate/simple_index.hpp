// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simple_index.h"
#include "simple_index_saver.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace search::predicate {

namespace simpleindex {
    bool log_enabled();
    void log_debug(vespalib::string &str);
}

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::insertIntoPosting(vespalib::datastore::EntryRef &ref, Key key, DocId doc_id, const Posting &posting)
{
    typename BTreeStore::KeyDataType addition(doc_id, posting);
    /*
     * Note: existing value is overwritten by new value without
     * cleanup. Data referenced by existing value might be leaked,
     * but PredicateIntervalStore::remove() is already a noop.
     */
    _btree_posting_lists.apply(ref, &addition, &addition + 1, nullptr, nullptr);
    insertIntoVectorPosting(ref, key, doc_id, posting);
    pruneBelowThresholdVectors();
}

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::insertIntoVectorPosting(vespalib::datastore::EntryRef ref, Key key, DocId doc_id, const Posting &posting) {
    assert(doc_id < _limit_provider.getDocIdLimit());
    auto it = _vector_posting_lists.find(key);
    if (it.valid()) {
        auto &vector = *it.getData();
        vector.ensure_size(doc_id + 1);
        vector[doc_id] = posting;
    } else {
        createVectorIfOverThreshold(ref, key);
    }
};

template <typename Posting, typename Key, typename DocId>
SimpleIndex<Posting, Key, DocId>::~SimpleIndex() {
    _btree_posting_lists.disableFreeLists();
    _btree_posting_lists.disable_entry_hold_list();

    for (auto it = _dictionary.begin(); it.valid(); ++it) {
        vespalib::datastore::EntryRef ref(it.getData());
        if (ref.valid()) {
            _btree_posting_lists.clear(ref);
        }
    }

    _vector_posting_lists.disableFreeLists();
    _vector_posting_lists.disable_entry_hold_list();
    _vector_posting_lists.clear();
    _vector_posting_lists.getAllocator().freeze();
    _vector_posting_lists.getAllocator().reclaim_all_memory();

    _dictionary.disableFreeLists();
    _dictionary.disable_entry_hold_list();
    _dictionary.clear();
    _dictionary.getAllocator().freeze();
    _dictionary.getAllocator().reclaim_all_memory();

    _btree_posting_lists.clearBuilder();
    _btree_posting_lists.freeze();
    _btree_posting_lists.reclaim_all_memory();
}

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::deserialize(vespalib::DataBuffer &buffer, PostingDeserializer<Posting> &deserializer,
                                              SimpleIndexDeserializeObserver<Key, DocId> &observer, uint32_t version)
{
    typename Dictionary::Builder builder(_dictionary.getAllocator());
    uint32_t size = buffer.readInt32();
    std::vector<vespalib::btree::BTreeKeyData<DocId, Posting>> postings;
    for (size_t i = 0; i < size; ++i) {
        uint32_t posting_size = buffer.readInt32();
        if (!posting_size)
            continue;
        postings.clear();
        Key key = buffer.readInt64();
        for (size_t j = 0; j < posting_size; ++j) {
            DocId doc_id;
            if (version == 0) {
                DocId raw_id = buffer.readInt32();
                doc_id  = raw_id >> 6;
                uint8_t k = static_cast<uint8_t>(raw_id & 0x3f);
                uint8_t min_feature = k == 0 ? k : k + 1;
                observer.notifyInsert(key, doc_id, min_feature);
            } else {
                doc_id = buffer.readInt32();
                // min-feature is stored in separate data structure for version > 0
                observer.notifyInsert(key, doc_id, 0);
            }
            postings.emplace_back(doc_id, deserializer.deserialize(buffer));
        }
        vespalib::datastore::EntryRef ref;
        _btree_posting_lists.apply(ref, &postings[0], &postings[postings.size()], 0, 0);
        builder.insert(key, ref);
    }
    _dictionary.assign(builder);
    commit();
}

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::addPosting(Key key, DocId doc_id, const Posting &posting) {
    auto iter = _dictionary.lowerBound(key);
    vespalib::datastore::EntryRef ref;
    if (iter.valid() && key == iter.getKey()) {
        ref = iter.getData();
        insertIntoPosting(ref, key, doc_id, posting);
        if (ref != iter.getData()) {
            _dictionary.thaw(iter);
            iter.writeData(ref);
        }
    } else {
        insertIntoPosting(ref, key, doc_id, posting);
        _dictionary.insert(iter, key, ref);
    }
}

template <typename Posting, typename Key, typename DocId>
std::pair<Posting, bool>
SimpleIndex<Posting, Key, DocId>::removeFromPostingList(Key key, DocId doc_id) {
    auto dict_it = _dictionary.find(key);
    if (!dict_it.valid()) {
        return std::make_pair(Posting(), false);
    }
    auto ref = dict_it.getData();
    assert(ref.valid());
    auto posting_it = _btree_posting_lists.begin(ref);
    assert(posting_it.valid());

    if (posting_it.getKey() < doc_id) {
        posting_it.binarySeek(doc_id);
    }
    if (!posting_it.valid() || posting_it.getKey() != doc_id) {
        return std::make_pair(Posting(), false);
    }

    Posting posting = posting_it.getData();
    vespalib::datastore::EntryRef original_ref(ref);
    _btree_posting_lists.apply(ref, nullptr, nullptr, &doc_id, &doc_id + 1);
    removeFromVectorPostingList(ref, key, doc_id);
    if (!ref.valid()) { // last posting was removed
        _dictionary.remove(dict_it);
    } else if (ref != original_ref) {  // ref changed. update dictionary.
        _dictionary.thaw(dict_it);
        dict_it.writeData(ref);
    }
    return std::make_pair(posting, true);
}

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::removeFromVectorPostingList(vespalib::datastore::EntryRef ref, Key key, DocId doc_id) {
    auto it = _vector_posting_lists.find(key);
    if (it.valid()) {
        if (!removeVectorIfBelowThreshold(ref, it)) {
            (*it.getData())[doc_id] = Posting();
        }
    }
};

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::pruneBelowThresholdVectors() {
    //  Check if it is time to prune any vector postings
    if (++_insert_remove_counter % _config.vector_prune_frequency > 0) return;

    for (auto posting_it = _vector_posting_lists.begin(); posting_it.valid();) {
        Key key = posting_it.getKey();
        auto dict_it = _dictionary.find(key);
        assert(dict_it.valid());
        if (!removeVectorIfBelowThreshold(dict_it.getData(), posting_it)) {
            ++posting_it;
        }
    }
};

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::promoteOverThresholdVectors() {
    for (auto it = _dictionary.begin(); it.valid(); ++it) {
        Key key = it.getKey();
        if (!_vector_posting_lists.find(key).valid()) {
            createVectorIfOverThreshold(it.getData(), key);
        }
    }
}

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::logVector(const char *action, Key key, size_t document_count, double ratio, size_t vector_length) const {
    if (!simpleindex::log_enabled()) return;
    auto msg = vespalib::make_string(
            "%s vector for key '%016" PRIx64 "' with length %zu. Contains %zu documents "
                    "(doc id limit %u, committed doc id limit %u, ratio %f, "
                    "vector count %zu)",
            action, key, vector_length, document_count, _limit_provider.getDocIdLimit(),
            _limit_provider.getCommittedDocIdLimit(), ratio, _vector_posting_lists.size());
    simpleindex::log_debug(msg);
}

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::createVectorIfOverThreshold(vespalib::datastore::EntryRef ref, Key key) {
    uint32_t doc_id_limit = _limit_provider.getDocIdLimit();
    size_t size = getDocumentCount(ref);
    double ratio = getDocumentRatio(size, doc_id_limit);
    if (shouldCreateVectorPosting(size, ratio)) {
        auto vector = new vespalib::RcuVectorBase<Posting>(_config.grow_strategy, _generation_holder);
        vector->unsafe_resize(doc_id_limit);
        _btree_posting_lists.foreach_unfrozen(
                ref, [&](DocId d, const Posting &p) { (*vector)[d] = p; });
        _vector_posting_lists.insert(key, std::shared_ptr<PostingVector>(vector));
        logVector("Created", key, size, ratio, vector->size());
    }
}

template <typename Posting, typename Key, typename DocId>
bool
SimpleIndex<Posting, Key, DocId>::removeVectorIfBelowThreshold(vespalib::datastore::EntryRef ref, typename VectorStore::Iterator &it) {
    size_t size = getDocumentCount(ref);
    double ratio = getDocumentRatio(size, _limit_provider.getDocIdLimit());
    if (shouldRemoveVectorPosting(size, ratio)) {
        Key key = it.getKey();
        size_t vector_length = it.getData()->size();
        _vector_posting_lists.remove(it);
        logVector("Removed", key, size, ratio, vector_length);
        return true;
    }
    return false;
}

template <typename Posting, typename Key, typename DocId>
double
SimpleIndex<Posting, Key, DocId>::getDocumentRatio(size_t document_count, uint32_t doc_id_limit) const {
    assert(doc_id_limit > 1);
    return document_count / static_cast<double>(doc_id_limit - 1);
};

template <typename Posting, typename Key, typename DocId>
size_t
SimpleIndex<Posting, Key, DocId>::getDocumentCount(vespalib::datastore::EntryRef ref) const {
    return _btree_posting_lists.size(ref);
};

template <typename Posting, typename Key, typename DocId>
size_t
SimpleIndex<Posting, Key, DocId>::get_frozen_document_count(vespalib::datastore::EntryRef ref) const {
    return _btree_posting_lists.frozenSize(ref);
};

template <typename Posting, typename Key, typename DocId>
bool
SimpleIndex<Posting, Key, DocId>::shouldRemoveVectorPosting(size_t size, double ratio) const {
    return size < _config.lower_vector_size_threshold || ratio < _config.lower_docid_freq_threshold;
};

template <typename Posting, typename Key, typename DocId>
bool
SimpleIndex<Posting, Key, DocId>::shouldCreateVectorPosting(size_t size, double ratio) const {
    return size >= _config.upper_vector_size_threshold && ratio >= _config.upper_docid_freq_threshold;
};

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::commit() {
    _dictionary.getAllocator().freeze();
    _btree_posting_lists.freeze();
    _vector_posting_lists.getAllocator().freeze();
}

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::reclaim_memory(generation_t oldest_used_gen) {
    _btree_posting_lists.reclaim_memory(oldest_used_gen);
    _dictionary.getAllocator().reclaim_memory(oldest_used_gen);
    _vector_posting_lists.getAllocator().reclaim_memory(oldest_used_gen);

}

template <typename Posting, typename Key, typename DocId>
void
SimpleIndex<Posting, Key, DocId>::assign_generation(generation_t current_gen) {
    _dictionary.getAllocator().assign_generation(current_gen);
    _btree_posting_lists.assign_generation(current_gen);
    _vector_posting_lists.getAllocator().assign_generation(current_gen);
}

template <typename Posting, typename Key, typename DocId>
vespalib::MemoryUsage
SimpleIndex<Posting, Key, DocId>::getMemoryUsage() const {
    vespalib::MemoryUsage combined;
    combined.merge(_dictionary.getMemoryUsage());
    combined.merge(_btree_posting_lists.getMemoryUsage());
    combined.merge(_vector_posting_lists.getMemoryUsage());
    for (auto it = _vector_posting_lists.begin(); it.valid(); ++it) {
        combined.merge(it.getData()->getMemoryUsage());
    }
    return combined;
};

template <typename Posting, typename Key, typename DocId>
std::unique_ptr<ISaver>
SimpleIndex<Posting, Key, DocId>::make_saver(std::unique_ptr<PostingSaver<Posting>> subsaver) const
{
    return std::make_unique<SimpleIndexSaver<Posting, Key, DocId>>(_dictionary.getFrozenView(), _btree_posting_lists, std::move(subsaver));
}

}
