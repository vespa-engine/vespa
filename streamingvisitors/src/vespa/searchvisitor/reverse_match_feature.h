// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

#include <functional>
#include <map>
#include <memory>
#include <string>

namespace document {
class DocumentTypeRepo;
}
namespace search::fef {
class IBlueprintRegistry;
}

namespace streaming {

class SearchEnvironment;

/**
 * Process-level context for the reverseMatch feature. All members may be empty
 * (e.g. in vespa-verify-ranksetup), in which case the feature never matches.
 */
struct ReverseMatchContext {
    std::shared_ptr<SearchEnvironment>                env;
    std::map<std::string, std::string>                config_ids; // document type -> documentdb config id
    // The document type repo of the active config, so input documents and field paths follow schema changes.
    std::function<std::shared_ptr<const document::DocumentTypeRepo>()> repo;
};

/**
 * reverseMatch(schema): evaluates the stored query of the current (saved query) document
 * against the input document passed base64-encoded in rank property 'reverseMatch.document'.
 *
 * Reads these attributes of the current document: query_tree (raw, protobuf QueryTree),
 * query_props (raw, FS4 property maps), rank_profile (string) and min_relevance (float).
 * Outputs the relevance when the stored query matches with relevance >= min_relevance, otherwise -inf.
 */
class ReverseMatchBlueprint : public search::fef::Blueprint {
    std::shared_ptr<const ReverseMatchContext> _context;
    std::string                                _schema;
    std::string                                _shared_state_key;

public:
    explicit ReverseMatchBlueprint(std::shared_ptr<const ReverseMatchContext> context);
    ~ReverseMatchBlueprint() override;
    void visitDumpFeatures(const search::fef::IIndexEnvironment&, search::fef::IDumpFeatureVisitor&) const override {}
    search::fef::Blueprint::UP createInstance() const override;
    search::fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const search::fef::IIndexEnvironment& env, const search::fef::ParameterList& params) override;
    void prepareSharedState(const search::fef::IQueryEnvironment& env,
                            search::fef::IObjectStore&            store) const override;
    search::fef::FeatureExecutor& createExecutor(const search::fef::IQueryEnvironment& env,
                                                 vespalib::Stash&                      stash) const override;
};

void register_reverse_match(search::fef::IBlueprintRegistry&           registry,
                            std::shared_ptr<const ReverseMatchContext> context);

} // namespace streaming
