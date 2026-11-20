// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_tensor_posting_attribute.h"

#include <vespa/check_require.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/i_enum_store_dictionary.h>
#include <vespa/vespalib/datastore/unique_store_remapper.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/vespalib/util/typify.h>

#include <vespa/searchlib/attribute/string_direct_posting_store_adapter.hpp>

#include <algorithm>
#include <cmath>
#include <limits>

using vespalib::IllegalArgumentException;
using vespalib::SharedStringRepo;
using vespalib::string_id;
using vespalib::typify_invoke;
using vespalib::datastore::EntryRef;
using vespalib::eval::TypedCells;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

namespace {

DictionaryConfig label_dictionary_config() {
    return {DictionaryConfig::Type::BTREE, DictionaryConfig::Match::CASED};
}

const search::attribute::Config& check_config(const search::attribute::Config& cfg) {
    const auto& type = cfg.tensorType();
    if (!type.is_sparse() || type.count_mapped_dimensions() != 1) {
        throw IllegalArgumentException(
            "Tensor label postings require a sparse tensor type with exactly one mapped dimension, got tensor type " +
            type.to_spec());
    }
    return cfg;
}

// Convert a cell value to a posting weight. Values that are not exactly representable
// as float (double cells only) are rounded up, so a posting weight is never below the
// cell value. NaN is stored as 0.
float to_posting_weight(double value) noexcept {
    if (std::isnan(value)) {
        return 0.0f;
    }
    float weight = static_cast<float>(value);
    if (static_cast<double>(weight) < value) {
        weight = std::nextafter(weight, std::numeric_limits<float>::infinity());
    }
    return weight;
}

struct ConvertCells {
    template <typename CT> static void invoke(TypedCells cells, std::vector<float>& dst) {
        auto src = cells.typify<CT>();
        dst.clear();
        for (auto cell : src) {
            dst.push_back(to_posting_weight(static_cast<double>(cell)));
        }
    }
};

} // namespace

vespalib::btree::FloatMinMaxAggregated
DirectTensorPostingAttribute::PostingStoreAdapter::get_weight_range(EntryRef posting_idx) const {
    return _posting_store.getAggregated(posting_idx);
}

DirectTensorPostingAttribute::DirectTensorPostingAttribute(std::string_view baseFileName, const Config& cfg)
    : DirectTensorAttribute(baseFileName, check_config(cfg)),
      _label_store(true, label_dictionary_config()),
      _posting_store(_label_store.get_dictionary(), getStatus(), getConfig()),
      _posting_store_adapter(_posting_store, _label_store, getConfig().getIsFilter()) {
    // We never want the default (empty string) value in the label dictionary.
    _label_store.clear_default_value_ref();
    setup_memory_usage_empty();
}

DirectTensorPostingAttribute::~DirectTensorPostingAttribute() {
    _posting_store.disableFreeLists();
    _posting_store.disable_entry_hold_list();
    clear_all_postings();
}

void DirectTensorPostingAttribute::clear_all_postings() {
    _posting_store.clearBuilder();
    incGeneration(); // Force freeze
    _label_store.get_dictionary().clear_all_posting_lists(
        [this](EntryRef posting_idx) { _posting_store.clear(posting_idx); });
    incGeneration(); // Force freeze
}

void DirectTensorPostingAttribute::collect_labels(EntryRef ref, std::vector<LabelWeight>& labels) const {
    labels.clear();
    const auto* tensor = _direct_store.get_tensor_ptr(ref);
    if (tensor == nullptr) {
        return;
    }
    std::vector<float> weights;
    auto               cells = tensor->cells();
    typify_invoke<1, TypifyCellType, ConvertCells>(cells.type, cells, weights);
    const auto& index = tensor->index();
    labels.reserve(index.size());
    auto view = index.create_view({});
    view->lookup({});
    string_id        label;
    string_id* const addr[1] = {&label};
    size_t           subspace = 0;
    while (view->next_result(addr, subspace)) {
        labels.emplace_back(label, weights[subspace]);
    }
    // labels are unique since there is exactly one mapped dimension and no indexed dimensions
    std::sort(labels.begin(), labels.end(), [](const auto& lhs, const auto& rhs) { return lhs.first < rhs.first; });
}

void DirectTensorPostingAttribute::add_to_posting_list(IEnumStore::Index idx, DocId docid, float weight) {
    const LabelPostingStore::KeyDataType addition(docid, weight);
    _label_store.get_dictionary().update_posting_list(
        idx, _label_store.get_comparator(), [this, &addition](EntryRef posting_idx) -> EntryRef {
            _posting_store.apply(posting_idx, &addition, &addition + 1, nullptr, nullptr);
            return posting_idx;
        });
}

void DirectTensorPostingAttribute::remove_from_posting_list(IEnumStore::Index idx, DocId docid) {
    const uint32_t removal = docid;
    _label_store.get_dictionary().update_posting_list(
        idx, _label_store.get_comparator(), [this, &removal](EntryRef posting_idx) -> EntryRef {
            _posting_store.apply(posting_idx, nullptr, nullptr, &removal, &removal + 1);
            return posting_idx;
        });
}

void DirectTensorPostingAttribute::on_tensor_ref_change(DocId docid, EntryRef old_ref, EntryRef new_ref) {
    std::vector<LabelWeight> old_labels;
    std::vector<LabelWeight> new_labels;
    collect_labels(old_ref, old_labels);
    collect_labels(new_ref, new_labels);
    auto updater = _label_store.make_batch_updater();
    auto old_it = old_labels.begin();
    auto new_it = new_labels.begin();
    while (old_it != old_labels.end() || new_it != new_labels.end()) {
        if (new_it == new_labels.end() || (old_it != old_labels.end() && old_it->first < new_it->first)) {
            // label removed
            auto              str = SharedStringRepo::Handle::string_from_id(old_it->first);
            IEnumStore::Index idx;
            CHECK(_label_store.find_index(str.c_str(), idx));
            remove_from_posting_list(idx, docid);
            updater.dec_ref_count(idx);
            ++old_it;
        } else if (old_it == old_labels.end() || new_it->first < old_it->first) {
            // label added
            auto str = SharedStringRepo::Handle::string_from_id(new_it->first);
            auto idx = updater.insert(str.c_str());
            updater.inc_ref_count(idx);
            add_to_posting_list(idx, docid, new_it->second);
            ++new_it;
        } else {
            // label kept, update weight if changed
            if (old_it->second != new_it->second) {
                auto              str = SharedStringRepo::Handle::string_from_id(new_it->first);
                IEnumStore::Index idx;
                CHECK(_label_store.find_index(str.c_str(), idx));
                add_to_posting_list(idx, docid, new_it->second);
            }
            ++old_it;
            ++new_it;
        }
    }
    updater.commit();
}

void DirectTensorPostingAttribute::rebuild_postings() {
    CHECK(_label_store.get_num_uniques() == 0);
    uint32_t docid_limit = _refVector.size();
    (void)_posting_store.resizeBitVectors(docid_limit, docid_limit);
    // Collect (label, posting) pairs; docids are visited in increasing order
    std::vector<std::pair<string_id, LabelPostingStore::KeyDataType>> entries;
    std::vector<LabelWeight>                                          labels;
    for (uint32_t docid = 0; docid < docid_limit; ++docid) {
        collect_labels(_refVector[docid].load_relaxed(), labels);
        for (const auto& [label, weight] : labels) {
            entries.emplace_back(label, LabelPostingStore::KeyDataType(docid, weight));
        }
    }
    std::stable_sort(entries.begin(), entries.end(),
                     [](const auto& lhs, const auto& rhs) { return lhs.first < rhs.first; });
    auto                                        updater = _label_store.make_batch_updater();
    std::vector<LabelPostingStore::KeyDataType> additions;
    for (auto it = entries.begin(); it != entries.end();) {
        auto label = it->first;
        additions.clear();
        for (; it != entries.end() && it->first == label; ++it) {
            additions.push_back(it->second);
        }
        auto str = SharedStringRepo::Handle::string_from_id(label);
        auto idx = updater.insert(str.c_str());
        _label_store.set_ref_count(idx, additions.size());
        _label_store.get_dictionary().update_posting_list(
            idx, _label_store.get_comparator(), [this, &additions](EntryRef posting_idx) -> EntryRef {
                _posting_store.apply(posting_idx, additions.data(), additions.data() + additions.size(), nullptr,
                                     nullptr);
                return posting_idx;
            });
    }
    updater.commit();
}

bool DirectTensorPostingAttribute::onLoad(vespalib::Executor* executor) {
    if (!DirectTensorAttribute::onLoad(executor)) {
        return false;
    }
    rebuild_postings();
    commit(CommitParam::UpdateStats::FORCE);
    return true;
}

bool DirectTensorPostingAttribute::addDoc(DocId& docId) {
    bool result = DirectTensorAttribute::addDoc(docId);
    if (_posting_store.resizeBitVectors(_refVector.size(), _refVector.capacity())) {
        incGeneration();
    }
    return result;
}

void DirectTensorPostingAttribute::onShrinkLidSpace() {
    DirectTensorAttribute::onShrinkLidSpace();
    uint32_t committed_doc_id_limit = getCommittedDocIdLimit();
    (void)_posting_store.resizeBitVectors(committed_doc_id_limit, committed_doc_id_limit);
}

void DirectTensorPostingAttribute::onCommit() {
    DirectTensorAttribute::onCommit();
    const auto& compaction_strategy = getConfig().getCompactionStrategy();
    auto        remapper = _label_store.consider_compact_values(compaction_strategy);
    if (remapper) {
        // No enum indexes are stored outside the dictionary.
        remapper->done();
        remapper.reset();
        incGeneration();
        updateStat(CommitParam::UpdateStats::FORCE);
    }
    if (_label_store.consider_compact_dictionary(compaction_strategy)) {
        incGeneration();
        updateStat(CommitParam::UpdateStats::FORCE);
    }
    if (_posting_store.consider_compact_worst_btree_nodes(compaction_strategy)) {
        incGeneration();
        updateStat(CommitParam::UpdateStats::FORCE);
    }
    if (_posting_store.consider_compact_worst_buffers(compaction_strategy)) {
        incGeneration();
        updateStat(CommitParam::UpdateStats::FORCE);
    }
}

vespalib::MemoryUsage DirectTensorPostingAttribute::update_stat() {
    auto        total = DirectTensorAttribute::update_stat();
    const auto& compaction_strategy = getConfig().getCompactionStrategy();
    total.merge(_label_store.update_stat(compaction_strategy));
    total.merge(_posting_store.update_stat(compaction_strategy));
    return total;
}

void DirectTensorPostingAttribute::reclaim_memory(vespalib::Generation oldest_used_gen) {
    DirectTensorAttribute::reclaim_memory(oldest_used_gen);
    _label_store.reclaim_memory(oldest_used_gen);
    _posting_store.reclaim_memory(oldest_used_gen);
}

void DirectTensorPostingAttribute::before_inc_generation(vespalib::Generation current_gen) {
    _posting_store.freeze();
    _label_store.freeze_dictionary();
    DirectTensorAttribute::before_inc_generation(current_gen);
    _label_store.assign_generation(current_gen);
    _posting_store.assign_generation(current_gen);
}

} // namespace search::tensor
