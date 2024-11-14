// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate_attribute.h"
#include "attribute_header.h"
#include "iattributesavetarget.h"
#include "load_utils.h"
#include "predicate_attribute_saver.h"
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/predicate/predicate.h>
#include <vespa/searchlib/predicate/i_saver.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/util/data_buffer_writer.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.predicate_attribute");

using document::Predicate;
using document::PredicateFieldValue;
using vespalib::DataBuffer;
using vespalib::IllegalStateException;
using vespalib::make_string;
using namespace search::predicate;

namespace search {

namespace {
constexpr uint8_t MAX_MIN_FEATURE = 255;
constexpr uint16_t MAX_INTERVAL_RANGE = static_cast<uint16_t>(predicate::MAX_INTERVAL);


int64_t
adjustBound(int32_t arity, int64_t bound) {
    int64_t adjusted = arity;
    int64_t value = bound;
    int64_t max = LLONG_MAX / arity;
    while ((value /= arity) > 0) {
        if (adjusted > max) {
            return bound;
        }
        adjusted *= arity;
    }
    return adjusted - 1;
}

int64_t
adjustLowerBound(int32_t arity, int64_t lower_bound) {
    if (lower_bound == LLONG_MIN) {
        return lower_bound;
    } else if (lower_bound > 0) {
        return 0ll;
    } else {
        return -adjustBound(arity, -lower_bound);
    }
}

int64_t
adjustUpperBound(int32_t arity, int64_t upper_bound) {
    if (upper_bound == LLONG_MAX) {
        return upper_bound;
    } else if (upper_bound < 0) {
        return -1ll;  // 0 belongs to the positive range.
    } else {
        return adjustBound(arity, upper_bound);
    }
}

SimpleIndexConfig createSimpleIndexConfig(const search::attribute::Config &config) {
    return SimpleIndexConfig(config.predicateParams().dense_posting_list_threshold(),
                             config.getGrowStrategy());
}

}  // namespace

PredicateAttribute::PredicateAttribute(const std::string &base_file_name)
    : PredicateAttribute(base_file_name, Config(BasicType::PREDICATE))
{}

PredicateAttribute::PredicateAttribute(const std::string &base_file_name, const Config &config)
    : NotImplementedAttribute(base_file_name, config),
      _limit_provider(*this),
      _index(std::make_unique<PredicateIndex>(getGenerationHolder(), _limit_provider,
                                              createSimpleIndexConfig(config), config.predicateParams().arity())),
      _lower_bound(adjustLowerBound(config.predicateParams().arity(), config.predicateParams().lower_bound())),
      _upper_bound(adjustUpperBound(config.predicateParams().arity(), config.predicateParams().upper_bound())),
      _min_feature(config.getGrowStrategy(), getGenerationHolder()),
      _interval_range_vector(config.getGrowStrategy(), getGenerationHolder()),
      _max_interval_range(1)
{
}

PredicateAttribute::~PredicateAttribute()
{
    getGenerationHolder().reclaim_all();
}

void PredicateAttribute::populateIfNeeded() {
    _index->populateIfNeeded(getNumDocs());
}

uint32_t
PredicateAttribute::getValueCount(DocId) const
{
    return 1;
}

void
PredicateAttribute::onCommit()
{
    _index->commit();
    populateIfNeeded();
    incGeneration();
}

void
PredicateAttribute::onUpdateStat()
{
    // update statistics
    vespalib::MemoryUsage combined;
    combined.merge(_min_feature.getMemoryUsage());
    combined.merge(_interval_range_vector.getMemoryUsage());
    combined.merge(_index->getMemoryUsage());
    combined.mergeGenerationHeldBytes(getGenerationHolder().get_held_bytes());
    this->updateStatistics(_min_feature.size(), _min_feature.size(),
                           combined.allocatedBytes(), combined.usedBytes(),
                           combined.deadBytes(), combined.allocatedBytesOnHold());
}

void
PredicateAttribute::reclaim_memory(generation_t oldest_used_gen)
{
    getGenerationHolder().reclaim(oldest_used_gen);
    _index->reclaim_memory(oldest_used_gen);
}

void
PredicateAttribute::before_inc_generation(generation_t current_gen)
{
    getGenerationHolder().assign_generation(current_gen);
    _index->assign_generation(current_gen);
}

std::unique_ptr<AttributeSaver>
PredicateAttribute::onInitSave(std::string_view fileName)
{
    auto guard(getGenerationHandler().takeGuard());
    auto header = this->createAttributeHeader(fileName);
    auto min_feature_view = _min_feature.make_read_view(_min_feature.size());
    auto interval_range_vector_view = _interval_range_vector.make_read_view(_interval_range_vector.size());
    return std::make_unique<PredicateAttributeSaver>
        (std::move(guard),
         std::move(header),
         getVersion(),
         _index->make_saver(),
         PredicateAttributeSaver::MinFeatureVector{ min_feature_view.begin(), min_feature_view.end() },
         PredicateAttributeSaver::IntervalRangeVector{ interval_range_vector_view.begin(), interval_range_vector_view.end() },
         _max_interval_range);
}

uint32_t
PredicateAttribute::getVersion() const {
    return PREDICATE_ATTRIBUTE_VERSION;
}

namespace {

template <typename V>
struct DocIdLimitFinderAndMinFeatureFiller : SimpleIndexDeserializeObserver<> {
    uint32_t _highest_doc_id;
    V & _min_feature;
    PredicateIndex &_index;
    DocIdLimitFinderAndMinFeatureFiller(V & min_feature, PredicateIndex &index) :
        _highest_doc_id(0),
        _min_feature(min_feature),
        _index(index)
    {}
    void notifyInsert(uint64_t, uint32_t doc_id, uint32_t min_feature) override {
        if (doc_id > _highest_doc_id) {
            _highest_doc_id = doc_id;
            _min_feature.ensure_size(doc_id + 1, PredicateAttribute::MIN_FEATURE_FILL);
        }
        _min_feature[doc_id] = min_feature;
    }
};

struct DummyObserver : SimpleIndexDeserializeObserver<> {
    DummyObserver()  {}
    void notifyInsert(uint64_t, uint32_t, uint32_t) override {}
};

}

bool
PredicateAttribute::onLoad(vespalib::Executor *)
{
    auto loaded_buffer = attribute::LoadUtils::loadDAT(*this);
    char *rawBuffer = const_cast<char *>(static_cast<const char *>(loaded_buffer->buffer()));
    size_t size = loaded_buffer->size();
    DataBuffer buffer(rawBuffer, size);
    buffer.moveFreeToData(size);

    const GenericHeader &header = loaded_buffer->getHeader();
    auto attributeHeader = attribute::AttributeHeader::extractTags(header, getBaseFileName());
    uint32_t version = attributeHeader.getVersion();

    setCreateSerialNum(attributeHeader.getCreateSerialNum());

    LOG(info, "Loading predicate attribute version %d. getVersion() = %d", version, getVersion());

    DocId highest_doc_id;
    if (version == 0) {
        DocIdLimitFinderAndMinFeatureFiller<MinFeatureVector> observer(_min_feature, *_index);
        _index = std::make_unique<PredicateIndex>(getGenerationHolder(), _limit_provider,
                                                  createSimpleIndexConfig(getConfig()), buffer, observer, 0);
        highest_doc_id = observer._highest_doc_id;
    } else {
        DummyObserver observer;
        _index = std::make_unique<PredicateIndex>(getGenerationHolder(), _limit_provider,
                                                  createSimpleIndexConfig(getConfig()), buffer, observer, version);
        highest_doc_id = buffer.readInt32();
        // Deserialize min feature vector
        _min_feature.ensure_size(highest_doc_id + 1, PredicateAttribute::MIN_FEATURE_FILL);
        for (uint32_t docId = 1; docId <= highest_doc_id; ++docId) {
            _min_feature[docId] = buffer.readInt8();
        }
    }
    _interval_range_vector.ensure_size(highest_doc_id + 1);
    // Interval ranges are only stored in version >= 2
    for (uint32_t docId = 1; docId <= highest_doc_id; ++docId) {
        _interval_range_vector[docId] = version < 2 ? MAX_INTERVAL_RANGE : buffer.readInt16();
    }
    _max_interval_range = version < 2 ? MAX_INTERVAL_RANGE : buffer.readInt16();
    if (buffer.getDataLen() != 0) {
        throw IllegalStateException(make_string("Deserialize error when loading predicate attribute '%s', %" PRId64 " bytes remaining in buffer", getName().c_str(), (int64_t) buffer.getDataLen()));
    }
    _index->adjustDocIdLimit(highest_doc_id);
    setNumDocs(highest_doc_id + 1);
    setCommittedDocIdLimit(highest_doc_id + 1);
    set_size_on_disk(loaded_buffer->size_on_disk());
    _index->onDeserializationCompleted();
    return true;
}

bool
PredicateAttribute::addDoc(DocId &doc_id)
{
    doc_id = getNumDocs();
    incNumDocs();
    updateUncommittedDocIdLimit(doc_id);
    _index->adjustDocIdLimit(doc_id);
    _interval_range_vector.ensure_size(doc_id + 1);
    _min_feature.ensure_size(doc_id + 1);
    return true;
}

uint32_t
PredicateAttribute::clearDoc(DocId doc_id)
{
    updateUncommittedDocIdLimit(doc_id);
    _index->removeDocument(doc_id);
    _min_feature[doc_id] = MIN_FEATURE_FILL;
    _interval_range_vector[doc_id] = 0;
    return 0;
}

void
PredicateAttribute::updateValue(uint32_t doc_id, const PredicateFieldValue &value)
{
    const auto &inspector = value.getSlime().get();

    _index->removeDocument(doc_id);
    updateUncommittedDocIdLimit(doc_id);

    long root_type = inspector[Predicate::NODE_TYPE].asLong();
    if (root_type == Predicate::TYPE_FALSE) {  // never match
        _min_feature[doc_id] = MIN_FEATURE_FILL;
        _interval_range_vector[doc_id] = 0;
        return;
    } else if (root_type == Predicate::TYPE_TRUE) {
        _min_feature[doc_id] = 0;
        _interval_range_vector[doc_id] = 0x1;
        _index->indexEmptyDocument(doc_id);
        return;
    }
    PredicateTreeAnnotations result;
    PredicateTreeAnnotator::annotate(inspector, result, _lower_bound, _upper_bound);
    _index->indexDocument(doc_id, result);
    assert(result.min_feature <= MAX_MIN_FEATURE);
    uint8_t minFeature = static_cast<uint8_t>(result.min_feature);
    _min_feature[doc_id] = minFeature;
    _interval_range_vector[doc_id] = result.interval_range;
    _max_interval_range = std::max(result.interval_range, _max_interval_range);
    assert(result.interval_range > 0);
}

}  // namespace search
