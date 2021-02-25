// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proto_converter.h"
#include <vespa/searchlib/common/mapnames.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/binary_format.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/util/size_literals.h>

namespace search::engine {

namespace {

template <typename T>
vespalib::string make_sort_spec(const T &sorting) {
    vespalib::string spec;
    for (const auto &field_spec: sorting) {
        if (!spec.empty()) {
            spec.push_back(' ');
        }
        if (field_spec.ascending()) {
            spec.push_back('+');
        } else {
            spec.push_back('-');
        }
        spec.append(field_spec.field());
    }
    return spec;
}

template <typename T>
void add_single_props(fef::Properties &dst, const T &src) {
    for (const auto &entry: src) {
        dst.add(entry.name(), entry.value());
    }
}

template <typename T>
void add_multi_props(fef::Properties &dst, const T &src) {
    for (const auto &entry: src) {
        for (int i = 0; i < entry.values_size(); ++i) {
            dst.add(entry.name(), entry.values(i));
        }
    }
}

}

//-----------------------------------------------------------------------------

void
ProtoConverter::search_request_from_proto(const ProtoSearchRequest &proto, SearchRequest &request)
{
    request.offset = proto.offset();
    request.maxhits = proto.hits();
    request.setTimeout(1ms * proto.timeout());
    request.setTraceLevel(proto.trace_level());
    request.sortSpec = make_sort_spec(proto.sorting());
    request.sessionId.assign(proto.session_key().begin(), proto.session_key().end());
    request.propertiesMap.lookupCreate(MapNames::MATCH).add("documentdb.searchdoctype", proto.document_type());
    if (proto.cache_grouping()) {
        request.propertiesMap.lookupCreate(MapNames::CACHES).add("grouping", "true");
    }
    if (proto.cache_query()) {
        request.propertiesMap.lookupCreate(MapNames::CACHES).add("query", "true");
    }
    request.ranking = proto.rank_profile();
    if ((proto.feature_overrides_size() + proto.tensor_feature_overrides_size()) > 0) {
        auto &feature_overrides = request.propertiesMap.lookupCreate(MapNames::FEATURE);
        add_multi_props(feature_overrides, proto.feature_overrides());
        add_single_props(feature_overrides, proto.tensor_feature_overrides());
    }
    if ((proto.rank_properties_size() + proto.tensor_rank_properties_size()) > 0) {
        auto &rank_props = request.propertiesMap.lookupCreate(MapNames::RANK);
        add_multi_props(rank_props, proto.rank_properties());
        add_single_props(rank_props, proto.tensor_rank_properties());
    }
    request.groupSpec.assign(proto.grouping_blob().begin(), proto.grouping_blob().end());
    request.location = proto.geo_location();
    request.stackDump.assign(proto.query_tree_blob().begin(), proto.query_tree_blob().end());
}

void
ProtoConverter::search_reply_to_proto(const SearchReply &reply, ProtoSearchReply &proto)
{
    proto.set_total_hit_count(reply.totalHitCount);
    proto.set_coverage_docs(reply.coverage.getCovered());
    proto.set_active_docs(reply.coverage.getActive());
    proto.set_soon_active_docs(reply.coverage.getSoonActive());
    proto.set_degraded_by_match_phase(reply.coverage.wasDegradedByMatchPhase());
    proto.set_degraded_by_soft_timeout(reply.coverage.wasDegradedByTimeout());
    bool has_sort_data = (reply.sortIndex.size() > 0);
    assert(!has_sort_data || (reply.sortIndex.size() == (reply.hits.size() + 1)));
    for (size_t i = 0; i < reply.hits.size(); ++i) {
        auto *hit = proto.add_hits();
        hit->set_global_id(reply.hits[i].gid.get(), document::GlobalId::LENGTH);
        hit->set_relevance(reply.hits[i].metric);
        if (has_sort_data) {
            size_t sort_data_offset = reply.sortIndex[i];
            size_t sort_data_size = (reply.sortIndex[i + 1] - reply.sortIndex[i]);
            assert((sort_data_offset + sort_data_size) <= reply.sortData.size());
            hit->set_sort_data(&reply.sortData[sort_data_offset], sort_data_size);
        }
    }
    proto.set_grouping_blob(&reply.groupResult[0], reply.groupResult.size());
    const auto &slime_trace = reply.propertiesMap.trace().lookup("slime");
    proto.set_slime_trace(slime_trace.get().data(), slime_trace.get().size());
}

//-----------------------------------------------------------------------------

void
ProtoConverter::docsum_request_from_proto(const ProtoDocsumRequest &proto, DocsumRequest &request)
{
    request.setTimeout(1ms * proto.timeout());
    request.sessionId.assign(proto.session_key().begin(), proto.session_key().end());
    request.propertiesMap.lookupCreate(MapNames::MATCH).add("documentdb.searchdoctype", proto.document_type());
    request.resultClassName = proto.summary_class();
    if (proto.cache_query()) {
        request.propertiesMap.lookupCreate(MapNames::CACHES).add("query", "true");
    }
    request.dumpFeatures = proto.dump_features();
    request.ranking = proto.rank_profile();
    if ((proto.feature_overrides_size() + proto.tensor_feature_overrides_size()) > 0) {
        auto &feature_overrides = request.propertiesMap.lookupCreate(MapNames::FEATURE);
        add_multi_props(feature_overrides, proto.feature_overrides());
        add_single_props(feature_overrides, proto.tensor_feature_overrides());
    }
    if ((proto.rank_properties_size() + proto.tensor_rank_properties_size()) > 0) {
        auto &rank_props = request.propertiesMap.lookupCreate(MapNames::RANK);
        add_multi_props(rank_props, proto.rank_properties());
        add_single_props(rank_props, proto.tensor_rank_properties());
    }
    if(proto.highlight_terms_size() > 0) {
        auto &highlight_terms = request.propertiesMap.lookupCreate(MapNames::HIGHLIGHTTERMS);
        add_multi_props(highlight_terms, proto.highlight_terms());
    }
    request.location = proto.geo_location();
    request.stackDump.assign(proto.query_tree_blob().begin(), proto.query_tree_blob().end());
    request.hits.resize(proto.global_ids_size());
    for (int i = 0; i < proto.global_ids_size(); ++i) {
        const auto &gid = proto.global_ids(i);
        if (gid.size() == document::GlobalId::LENGTH) {
            request.hits[i].gid = document::GlobalId(gid.data());
        }
    }
}

void
ProtoConverter::docsum_reply_to_proto(const DocsumReply &reply, ProtoDocsumReply &proto)
{
    if (reply._root) {
        vespalib::SmartBuffer buf(4_Ki);
        vespalib::slime::BinaryFormat::encode(*reply._root, buf);
        proto.set_slime_summaries(buf.obtain().data, buf.obtain().size);
    }
}

//-----------------------------------------------------------------------------

void
ProtoConverter::monitor_request_from_proto(const ProtoMonitorRequest &proto, MonitorRequest &request)
{
    (void) proto;
    request.reportActiveDocs = true;
}

void
ProtoConverter::monitor_reply_to_proto(const MonitorReply &reply, ProtoMonitorReply &proto)
{
    proto.set_online(reply.timestamp != 0);
    proto.set_active_docs(reply.activeDocs);
    proto.set_distribution_key(reply.distribution_key);
    proto.set_is_blocking_writes(reply.is_blocking_writes);
}

//-----------------------------------------------------------------------------

}
