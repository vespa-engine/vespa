// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reverse_match_feature.h"

#include "searchenvironment.h"
#include "single_document_matcher.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/eval/eval/value.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/iblueprintregistry.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/objectstore.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/encoding/base64.h>
#include <vespa/vespalib/hwaccelerated/functions.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stash.h>

#include <algorithm>
#include <cmath>
#include <limits>
#include <optional>

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
// The full check computes the distance in float precision: only reject when clearly beyond the threshold.
constexpr double prefilter_margin = 1e-3;

using vespalib::eval::CellType;
using vespalib::eval::TypedCells;

// Dense float or double cells as doubles, nullopt for other cell types.
std::optional<std::vector<double>> to_doubles(TypedCells cells) {
    std::vector<double> out;
    if (cells.type == CellType::FLOAT) {
        auto span = cells.unsafe_typify<float>();
        out.assign(span.begin(), span.end());
    } else if (cells.type == CellType::DOUBLE) {
        auto span = cells.unsafe_typify<double>();
        out.assign(span.begin(), span.end());
    } else {
        return std::nullopt;
    }
    return out;
}

// Angle (Vespa's angular distance) between stored query cells and a unit vector, nullopt when not comparable.
// Float cells (the common case) are compared in place with SIMD dot products: no copy per candidate.
std::optional<double> angle(TypedCells cells, const std::vector<float>& unit) {
    if (cells.non_existing_attribute_value() || cells.size != unit.size()) {
        return std::nullopt;
    }
    double dot = 0.0;
    double norm = 0.0;
    if (cells.type == CellType::FLOAT) {
        const float* values = cells.unsafe_typify<float>().data();
        dot = vespalib::hwaccelerated::dot_product(values, unit.data(), unit.size());
        norm = vespalib::hwaccelerated::dot_product(values, values, unit.size());
    } else if (auto values = to_doubles(cells)) {
        for (size_t i = 0; i < unit.size(); ++i) {
            dot += (*values)[i] * unit[i];
            norm += (*values)[i] * (*values)[i];
        }
    } else {
        return std::nullopt;
    }
    if (norm <= 0.0) {
        return std::nullopt;
    }
    return std::acos(std::clamp(dot / std::sqrt(norm), -1.0, 1.0));
}

// Decoded once per query, shared (read only) by all match threads.
struct SharedState {
    std::shared_ptr<const SearchEnvironmentSnapshot> snapshot;
    std::shared_ptr<const document::DocumentTypeRepo> repo;
    std::unique_ptr<const document::Document>        document;
    std::vector<float> prefilter_unit; // the input document's prefilter vector, normalized; empty if none
};
using SharedStateWrapper = AnyWrapper<SharedState>;

class ReverseMatchExecutor : public FeatureExecutor {
    std::unique_ptr<SingleDocumentMatcher>   _matcher;
    const std::vector<float>*                _prefilter_unit;
    const search::tensor::ITensorAttribute*  _prefilter_vector;
    const IAttributeVector*                  _prefilter_distance;
    const IAttributeVector*                  _query_tree;
    const IAttributeVector*                  _query_props;
    const IAttributeVector*                  _rank_profile;
    const IAttributeVector*                  _min_relevance;
    search::attribute::ConstCharContent      _profile_buf;

public:
    ReverseMatchExecutor(std::unique_ptr<SingleDocumentMatcher> matcher, const std::vector<float>* prefilter_unit,
                         const IQueryEnvironment& env)
        : _matcher(std::move(matcher)),
          _prefilter_unit(prefilter_unit),
          _prefilter_vector(nullptr),
          _prefilter_distance(env.getAttributeContext().getAttribute("prefilter_distance")),
          _query_tree(env.getAttributeContext().getAttribute("query_tree")),
          _query_props(env.getAttributeContext().getAttribute("query_props")),
          _rank_profile(env.getAttributeContext().getAttribute("rank_profile")),
          _min_relevance(env.getAttributeContext().getAttribute("min_relevance")),
          _profile_buf() {
        const auto* vector = env.getAttributeContext().getAttribute("prefilter_vector");
        const auto* tensor = vector != nullptr ? vector->asTensorAttribute() : nullptr;
        if (tensor != nullptr && tensor->supports_extract_cells_ref() && tensor->getTensorType().is_dense()) {
            _prefilter_vector = tensor;
        }
    }

    // Exact and cheap: a stored query whose required nearest neighbor clause is beyond its distance threshold
    // cannot match, so it is rejected without parsing and running the query. 99%+ of vector candidates.
    bool rejected_by_prefilter(uint32_t docid) const {
        if (_prefilter_unit == nullptr || _prefilter_unit->empty() || _prefilter_vector == nullptr ||
            _prefilter_distance == nullptr) {
            return false;
        }
        double limit = _prefilter_distance->getFloat(docid);
        if (!(limit > 0.0) || !std::isfinite(limit)) {
            return false;
        }
        auto distance = angle(_prefilter_vector->extract_cells_ref(docid), *_prefilter_unit);
        return distance.has_value() && *distance > limit + prefilter_margin;
    }

    void execute(uint32_t docid) override {
        double out = no_match;
        if (rejected_by_prefilter(docid)) {
            outputs().set_number(0, out);
            return;
        }
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

bool ReverseMatchBlueprint::setup(const IIndexEnvironment& env, const ParameterList& params) {
    _schema = params[0].getValue();
    _prefilter_field = env.getProperties().lookup("reverseMatch.prefilterField").get();
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
        if (!_prefilter_field.empty()) {
            state.prefilter_unit = prefilter_unit(*state.document);
        }
        if (state.snapshot) {
            store.add(_shared_state_key, std::make_unique<SharedStateWrapper>(std::move(state)));
        } else {
            LOG(warning, "No streaming config snapshot for '%s'", config_id->second.c_str());
        }
    } catch (const std::exception& e) {
        LOG(warning, "reverseMatch(%s): could not prepare input document: %s", _schema.c_str(), e.what());
    }
}

std::vector<float> ReverseMatchBlueprint::prefilter_unit(const document::Document& doc) const {
    try {
        auto value = doc.getValue(_prefilter_field);
        const auto* tensor = dynamic_cast<const document::TensorFieldValue*>(value.get());
        if (tensor == nullptr || tensor->getAsTensorPtr() == nullptr || !tensor->getAsTensorPtr()->type().is_dense()) {
            return {};
        }
        auto values = to_doubles(tensor->getAsTensorPtr()->cells());
        if (!values) {
            return {};
        }
        double norm = 0.0;
        for (double v : *values) {
            norm += v * v;
        }
        if (norm <= 0.0) {
            return {};
        }
        std::vector<float> unit;
        unit.reserve(values->size());
        for (double v : *values) {
            unit.push_back(static_cast<float>(v / std::sqrt(norm)));
        }
        return unit;
    } catch (const std::exception& e) {
        LOG(debug, "reverseMatch(%s): no prefilter vector '%s': %s", _schema.c_str(), _prefilter_field.c_str(), e.what());
        return {};
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
    const std::vector<float>* prefilter_unit = nullptr;
    if (const Anything* any = env.getObjectStore().get(_shared_state_key)) {
        prefilter_unit = &SharedStateWrapper::getValue(*any).prefilter_unit;
    }
    return stash.create<ReverseMatchExecutor>(std::move(matcher), prefilter_unit, env);
}

void register_reverse_match(search::fef::IBlueprintRegistry&           registry,
                            std::shared_ptr<const ReverseMatchContext> context) {
    registry.addPrototype(std::make_shared<ReverseMatchBlueprint>(std::move(context)));
}

} // namespace streaming
