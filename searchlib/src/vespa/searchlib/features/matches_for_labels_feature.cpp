// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matches_for_labels_feature.h"

#include "constant_tensor_executor.h"
#include "utils.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include <vespa/searchlib/fef/match_data_details.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/tensor/fast_value_view.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/vespalib/util/stash.h>

#include <algorithm>

namespace search::features {

using fef::Blueprint;
using fef::FeatureExecutor;
using fef::FeatureType;
using fef::IllegalHandle;
using fef::ITermData;
using fef::ITermFieldData;
using fef::ITermFieldRangeAdapter;
using fef::MatchDataDetails;
using fef::TermFieldHandle;
using search::tensor::FastValueView;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TypedCells;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace {

/**
 * Executor returning 1 for each query item label whose terms matched, as a
 * tensor<float>(label{}) with one cell per label that matched the document.
 * Which handles are considered per label is decided by the blueprint; the
 * executor itself does not care about fields.
 */
class MatchesForLabelsExecutor : public fef::FeatureExecutor {
    std::vector<std::vector<TermFieldHandle>> _handles_per_label; // in label order
    vespalib::SharedStringRepo::Handles       _labels;            // resolved once
    vespalib::StringIdVector                  _view_labels;       // per-doc matching subset
    std::vector<float>                        _view_cells;        // parallel; 1.f per matching label
    const vespalib::eval::Value&              _empty_output;
    std::unique_ptr<vespalib::eval::Value>    _output;
    const fef::MatchData*                     _md;

public:
    MatchesForLabelsExecutor(std::vector<std::pair<std::string, std::vector<TermFieldHandle>>> labeled_handles,
                             const vespalib::eval::Value&                                      empty_output);
    void handle_bind_match_data(const fef::MatchData& match_data) override;
    void execute(uint32_t doc_id) override;
};

MatchesForLabelsExecutor::MatchesForLabelsExecutor(
    std::vector<std::pair<std::string, std::vector<TermFieldHandle>>> labeled_handles, const Value& empty_output)
    : FeatureExecutor(),
      _handles_per_label(),
      _labels(),
      _view_labels(),
      _view_cells(),
      _empty_output(empty_output),
      _output(),
      _md(nullptr) {
    _handles_per_label.reserve(labeled_handles.size());
    _labels.reserve(labeled_handles.size());
    for (auto& [label, handles] : labeled_handles) {
        // Labels are resolved once here, not per document, since every document uses the same labels.
        _labels.add(label);
        _handles_per_label.push_back(std::move(handles));
    }
    _view_labels.reserve(labeled_handles.size());
    _view_cells.reserve(labeled_handles.size());
}

void MatchesForLabelsExecutor::handle_bind_match_data(const fef::MatchData& match_data) {
    _md = &match_data;
}

void MatchesForLabelsExecutor::execute(uint32_t doc_id) {
    _view_labels.clear();
    _view_cells.clear();
    const auto& labels = _labels.view();
    for (uint32_t i = 0, m = _handles_per_label.size(); i < m; ++i) {
        if (std::any_of(_handles_per_label[i].begin(), _handles_per_label[i].end(),
                        [this, doc_id](TermFieldHandle handle) {
                            return _md->resolveTermField(handle)->has_ranking_data(doc_id);
                        }))
        {
            _view_labels.push_back(labels[i]);
            _view_cells.push_back(1.0f);
        }
    }
    if (_view_cells.empty()) {
        outputs().set_object(0, _empty_output);
        return;
    }
    _output = std::make_unique<FastValueView>(_empty_output.type(), _view_labels, TypedCells(_view_cells), 1,
                                              _view_cells.size());
    outputs().set_object(0, *_output);
}

// Only the document ID is needed, so request the cheaper Interleaved details.
// SimpleTermFieldData discards the requested detail level, so tests cannot
// distinguish this from Normal.
void add_handle(const ITermFieldData& tfd, std::vector<TermFieldHandle>& handles) {
    TermFieldHandle handle = tfd.getHandle(MatchDataDetails::Interleaved);
    if (handle != IllegalHandle) {
        handles.push_back(handle);
    }
}

/**
 * Collect the handles to look at for one label. With a field id only the handle
 * for that field is used; without one every field searched by the term counts,
 * including the reserved "no field" used by query items searching no field.
 */
std::vector<TermFieldHandle> collect_handles(const std::vector<const ITermData*>& terms,
                                             std::optional<uint32_t>              field_id) {
    std::vector<TermFieldHandle> handles;
    for (const ITermData* term : terms) {
        if (field_id.has_value()) {
            const ITermFieldData* tfd = term->lookupField(field_id.value());
            if (tfd != nullptr) {
                add_handle(*tfd, handles);
            }
        } else {
            for (ITermFieldRangeAdapter fields(*term); fields.valid(); fields.next()) {
                add_handle(fields.get(), handles);
            }
        }
    }
    return handles;
}

} // namespace

MatchesForLabelsBlueprint::MatchesForLabelsBlueprint()
    : Blueprint("matches_for_labels"),
      _field_id(),
      _value_type(ValueType::from_spec("tensor<float>(label{})")),
      _empty_output() {
}

MatchesForLabelsBlueprint::~MatchesForLabelsBlueprint() = default;

void MatchesForLabelsBlueprint::visitDumpFeatures(const fef::IIndexEnvironment&, fef::IDumpFeatureVisitor&) const {
}

Blueprint::UP MatchesForLabelsBlueprint::createInstance() const {
    return std::make_unique<MatchesForLabelsBlueprint>();
}

fef::ParameterDescriptions MatchesForLabelsBlueprint::getDescriptions() const {
    return fef::ParameterDescriptions().desc().field().desc();
}

bool MatchesForLabelsBlueprint::setup(const fef::IIndexEnvironment&, const fef::ParameterList& params) {
    if (!params.empty()) {
        _field_id = params[0].asField()->id();
    }
    _empty_output = vespalib::eval::value_from_spec(_value_type.to_spec(), FastValueBuilderFactory::get());
    describeOutput("out",
                   _field_id.has_value()
                       ? "1 for each query item label whose terms matched the given field, as a "
                         "tensor<float>(label{}) with the labels as cell labels. Labels that did not "
                         "match have no cell."
                       : "1 for each query item label whose terms matched the document in any field, as a "
                         "tensor<float>(label{}) with the labels as cell labels. Labels that did not "
                         "match have no cell.",
                   FeatureType::object(_value_type));
    return true;
}

FeatureExecutor& MatchesForLabelsBlueprint::createExecutor(const fef::IQueryEnvironment& env,
                                                           vespalib::Stash&              stash) const {
    std::vector<std::pair<std::string, std::vector<TermFieldHandle>>> labeled_handles;
    for (auto& [label, terms] : util::getTermsByAllLabels(env)) {
        auto handles = collect_handles(terms, _field_id);
        if (!handles.empty()) {
            labeled_handles.emplace_back(std::move(label), std::move(handles));
        }
    }
    if (labeled_handles.empty()) {
        return stash.create<ConstantTensorRefExecutor>(*_empty_output);
    }
    return stash.create<MatchesForLabelsExecutor>(std::move(labeled_handles), *_empty_output);
}

} // namespace search::features
