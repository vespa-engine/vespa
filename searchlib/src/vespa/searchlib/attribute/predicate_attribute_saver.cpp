// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate_attribute_saver.h"

#include "iattributesavetarget.h"

#include <vespa/searchlib/predicate/i_saver.h>
#include <vespa/searchlib/predicate/nbo_write.h>

#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.predicate_attribute_saver");

using search::predicate::nbo_write;
using vespalib::GenerationGuard;

namespace search {

PredicateAttributeSaver::PredicateAttributeSaver(GenerationGuard&& guard, const attribute::AttributeHeader& header,
                                                 uint32_t version, std::unique_ptr<predicate::ISaver> index_saver,
                                                 MinFeatureVectorSnapshot    min_feature_snapshot,
                                                 IntervalRangeVectorSnapshot interval_range_vector_snapshot,
                                                 uint16_t                    max_interval_range)
    : AttributeSaver(std::move(guard), header),
      _version(version),
      _index_saver(std::move(index_saver)),
      _min_feature_snapshot(std::move(min_feature_snapshot)),
      _interval_range_vector_snapshot(std::move(interval_range_vector_snapshot)),
      _max_interval_range(max_interval_range) {
}

PredicateAttributeSaver::~PredicateAttributeSaver() = default;

bool PredicateAttributeSaver::onSave(IAttributeSaveTarget& save_target) {
    auto name = std::filesystem::path(get_file_name()).filename().native();
    LOG(info, "Saving predicate attribute version %u name '%s'", _version, name.c_str());
    auto writer = save_target.datWriter().allocBufferWriter();
    _index_saver->save(*writer);
    auto     min_feature_span = _min_feature_snapshot.span();
    uint32_t highest_doc_id = static_cast<uint32_t>(min_feature_span.size() - 1);
    nbo_write<uint32_t>(*writer, highest_doc_id);
    if (highest_doc_id > 0) {
        writer->write(&min_feature_span[1], highest_doc_id);
    }
    auto interval_range_vector_span = _interval_range_vector_snapshot.span();
    for (size_t i = 1; i <= highest_doc_id; ++i) {
        nbo_write<uint16_t>(*writer, interval_range_vector_span[i]);
    }
    nbo_write<uint16_t>(*writer, _max_interval_range);
    writer->flush();
    return true;
}

} // namespace search
