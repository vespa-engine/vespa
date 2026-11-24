// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reverse_match_feature.h"

#include "searchenvironment.h"
#include "single_document_matcher.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/iblueprintregistry.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/objectstore.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/encoding/base64.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stash.h>

#include <limits>

#include <vespa/log/log.h>
LOG_SETUP(".searchvisitor.reverse_match_feature");

using search::attribute::IAttributeVector;
using search::fef::Anything;
using search::fef::AnyWrapper;
using search::fef::FeatureExecutor;
using search::fef::IIndexEnvironment;
using search::fef::IObjectStore;
using search::fef::IQueryEnvironment;
using search::fef::ParameterList;

namespace streaming {

namespace {

constexpr double no_match = -std::numeric_limits<double>::infinity();

// Decoded once per query, shared (read only) by all match threads.
struct SharedState {
    std::shared_ptr<const SearchEnvironmentSnapshot> snapshot;
    std::shared_ptr<const document::DocumentTypeRepo> repo;
    std::unique_ptr<const document::Document>        document;
};
using SharedStateWrapper = AnyWrapper<SharedState>;

class ReverseMatchExecutor : public FeatureExecutor {
    std::unique_ptr<SingleDocumentMatcher>   _matcher;
    const IAttributeVector*                  _query_tree;
    const IAttributeVector*                  _query_props;
    const IAttributeVector*                  _rank_profile;
    const IAttributeVector*                  _min_relevance;
    search::attribute::ConstCharContent      _profile_buf;

public:
    ReverseMatchExecutor(std::unique_ptr<SingleDocumentMatcher> matcher, const IQueryEnvironment& env)
        : _matcher(std::move(matcher)),
          _query_tree(env.getAttributeContext().getAttribute("query_tree")),
          _query_props(env.getAttributeContext().getAttribute("query_props")),
          _rank_profile(env.getAttributeContext().getAttribute("rank_profile")),
          _min_relevance(env.getAttributeContext().getAttribute("min_relevance")),
          _profile_buf() {}

    void execute(uint32_t docid) override {
        double out = no_match;
        if (_matcher && _query_tree && _query_props && _rank_profile && _min_relevance) {
            _profile_buf.fill(*_rank_profile, docid);
            std::string profile = (_profile_buf.size() > 0 && _profile_buf[0] != nullptr) ? _profile_buf[0] : "default";
            auto result = _matcher->evaluate(_query_tree->get_raw(docid), _query_props->get_raw(docid), profile);
            if (result.has_value() && result->matched && result->score >= _min_relevance->getFloat(docid)) {
                out = result->score;
            }
        }
        outputs().set_number(0, out);
    }
};

} // namespace

ReverseMatchBlueprint::ReverseMatchBlueprint(std::shared_ptr<const ReverseMatchContext> context)
    : Blueprint("reverseMatch"), _context(std::move(context)), _schema(), _shared_state_key() {
}

ReverseMatchBlueprint::~ReverseMatchBlueprint() = default;

search::fef::Blueprint::UP ReverseMatchBlueprint::createInstance() const {
    return std::make_unique<ReverseMatchBlueprint>(_context);
}

search::fef::ParameterDescriptions ReverseMatchBlueprint::getDescriptions() const {
    return search::fef::ParameterDescriptions().desc().string();
}

bool ReverseMatchBlueprint::setup(const IIndexEnvironment&, const ParameterList& params) {
    _schema = params[0].getValue();
    _shared_state_key = "reverseMatch.shared." + _schema;
    describeOutput("score", "relevance of the stored query against the input document, or -inf if no match");
    return true;
}

void ReverseMatchBlueprint::prepareSharedState(const IQueryEnvironment& env, IObjectStore& store) const {
    if (store.get(_shared_state_key) != nullptr || !_context || !_context->env || !_context->repo) {
        return;
    }
    auto config_id = _context->config_ids.find(_schema);
    auto encoded = env.getProperties().lookup("reverseMatch.document").get();
    if (config_id == _context->config_ids.end() || encoded.empty()) {
        return;
    }
    try {
        SharedState state;
        state.snapshot = _context->env->get_snapshot(config_id->second);
        state.repo = _context->repo();
        if (!state.repo) {
            return;
        }
        std::string         bytes = vespalib::Base64::decode(encoded);
        vespalib::nbostream stream(bytes.data(), bytes.size());
        state.document = std::make_unique<const document::Document>(*state.repo, stream);
        if (state.snapshot) {
            store.add(_shared_state_key, std::make_unique<SharedStateWrapper>(std::move(state)));
        } else {
            LOG(warning, "No streaming config snapshot for '%s'", config_id->second.c_str());
        }
    } catch (const std::exception& e) {
        LOG(warning, "reverseMatch(%s): could not prepare input document: %s", _schema.c_str(), e.what());
    }
}

FeatureExecutor& ReverseMatchBlueprint::createExecutor(const IQueryEnvironment& env, vespalib::Stash& stash) const {
    std::unique_ptr<SingleDocumentMatcher> matcher;
    if (const Anything* any = env.getObjectStore().get(_shared_state_key)) {
        const auto& state = SharedStateWrapper::getValue(*any);
        try {
            matcher = std::make_unique<SingleDocumentMatcher>(state.snapshot, state.repo, *state.document);
        } catch (const std::exception& e) {
            LOG(warning, "reverseMatch(%s): could not set up matcher: %s", _schema.c_str(), e.what());
        }
    }
    return stash.create<ReverseMatchExecutor>(std::move(matcher), env);
}

void register_reverse_match(search::fef::IBlueprintRegistry&           registry,
                            std::shared_ptr<const ReverseMatchContext> context) {
    registry.addPrototype(std::make_shared<ReverseMatchBlueprint>(std::move(context)));
}

} // namespace streaming
