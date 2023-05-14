// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldmatchfeature.h"
#include "utils.h"
#include <vespa/searchlib/features/fieldmatch/computer.h>
#include <vespa/searchlib/features/fieldmatch/computer_shared_state.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/phrase_splitter_query_env.h>
#include <vespa/searchlib/fef/phrasesplitter.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;
using CollectionType = FieldInfo::CollectionType;

namespace search::features {

class FieldMatchExecutorSharedState : public Anything {
private:
    PhraseSplitterQueryEnv _splitter_env;
    fieldmatch::ComputerSharedState _cmp_shared_state;
public:
    FieldMatchExecutorSharedState(const fef::IQueryEnvironment& query_env,
                                  const fef::FieldInfo& field,
                                  const fieldmatch::Params& params);
    ~FieldMatchExecutorSharedState() override;
    const PhraseSplitterQueryEnv& get_phrase_splitter_query_env() const { return _splitter_env; }
    const fieldmatch::ComputerSharedState &get_computer_shared_state() const { return _cmp_shared_state; }
};

FieldMatchExecutorSharedState::FieldMatchExecutorSharedState(const IQueryEnvironment& query_env,
                                                             const FieldInfo& field,
                                                             const fieldmatch::Params& params)
    : Anything(),
      _splitter_env(query_env, field.id()),
      _cmp_shared_state(vespalib::make_string("fieldMatch(%s)", field.name().c_str()), _splitter_env, field, params)
{
}

FieldMatchExecutorSharedState::~FieldMatchExecutorSharedState() = default;

/**
 * Implements the executor for THE field match feature.
 */
class FieldMatchExecutor : public fef::FeatureExecutor {
private:
    fef::PhraseSplitter    _splitter;
    fieldmatch::Computer   _cmp;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    FieldMatchExecutor(const FieldMatchExecutorSharedState& shared_state);
    void execute(uint32_t docId) override;
};

FieldMatchExecutor::FieldMatchExecutor(const FieldMatchExecutorSharedState& shared_state)
    : FeatureExecutor(),
      _splitter(shared_state.get_phrase_splitter_query_env()),
      _cmp(shared_state.get_computer_shared_state(), _splitter)
{
}

void
FieldMatchExecutor::execute(uint32_t docId)
{
    //LOG(info, "execute for field '%s' and docId(%u)", _field.name().c_str(), docId);

    _splitter.update();
    _cmp.reset(docId);

    const fieldmatch::SimpleMetrics & simple = _cmp.getSimpleMetrics();

    // only run the computer if we have at least one match with position information
    // and that the matches with position information have valid field lengths
    bool runCmp = (simple.getMatches() > 0 &&
                   simple.getMatchesWithPosOcc() > 0 &&
                   !simple.getMatchWithInvalidFieldLength());

    //LOG(info, "runCmp(%s), simpleMetrics(%s)", runCmp ? "true" : "false", simple.toString().c_str());

    if (runCmp) {
        _cmp.run();
    }

    const fieldmatch::Metrics & result = _cmp.getFinalMetrics();

    outputs().set_number(0, runCmp ? result.getMatch() : 0); // score
    outputs().set_number(1, runCmp ? result.getProximity() : 0); // proximity
    outputs().set_number(2, runCmp ? result.getCompleteness() : simple.getCompleteness()); // completeness
    outputs().set_number(3, runCmp ? result.getQueryCompleteness() : simple.getQueryCompleteness()); // queryCompleteness
    outputs().set_number(4, result.getFieldCompleteness()); // fieldCompleteness
    outputs().set_number(5, runCmp ? result.getOrderness() : 0); // orderness
    outputs().set_number(6, result.getRelatedness()); // relatedness
    outputs().set_number(7, result.getEarliness()); // earliness
    outputs().set_number(8, result.getLongestSequenceRatio()); // longestSequenceRatio
    outputs().set_number(9, result.getSegmentProximity()); // segmentProximity
    outputs().set_number(10, runCmp ? result.getUnweightedProximity() : 0); // unweightedProximity
    outputs().set_number(11, runCmp ? result.getAbsoluteProximity() : 0); // absoluteProximity
    outputs().set_number(12, result.getOccurrence()); // occurrence
    outputs().set_number(13, result.getAbsoluteOccurrence()); // absoluteOccurence
    outputs().set_number(14, result.getWeightedOccurrence()); // weightedOccurence
    outputs().set_number(15, result.getWeightedAbsoluteOccurrence()); // weightedAbsoluteOccurence
    outputs().set_number(16, result.getSignificantOccurrence()); // significantOccurence

    outputs().set_number(17, runCmp ? result.getWeight() : simple.getWeight()); // weight
    outputs().set_number(18, result.getSignificance()); // significance
    outputs().set_number(19, result.getImportance()); // importance

    outputs().set_number(20, result.getSegments()); // segments
    outputs().set_number(21, runCmp ? result.getMatches() : simple.getMatches()); // matches
    outputs().set_number(22, result.getOutOfOrder()); // outOfOrder
    outputs().set_number(23, result.getGaps()); // gaps
    outputs().set_number(24, result.getGapLength()); // gapLength
    outputs().set_number(25, runCmp ? result.getLongestSequence() : 0); // longestSequence
    outputs().set_number(26, runCmp ? result.getHead() : 0); // head
    outputs().set_number(27, runCmp ? result.getTail() : 0); // tail
    outputs().set_number(28, result.getSegmentDistance()); // segmentDistance
    outputs().set_number(29, simple.getDegradedMatches()); // degradedMatches
}

void
FieldMatchExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _splitter.bind_match_data(md);
}

FieldMatchBlueprint::FieldMatchBlueprint() :
    Blueprint("fieldMatch"),
    _field(nullptr),
    _shared_state_key(),
    _params()
{
}

FieldMatchBlueprint::~FieldMatchBlueprint() = default;

void
FieldMatchBlueprint::visitDumpFeatures(const IIndexEnvironment & env,
                                       IDumpFeatureVisitor & visitor) const
{
    for (uint32_t i = 0; i < env.getNumFields(); ++i) {
        const search::fef::FieldInfo * field = env.getField(i);
        if (field->type() == search::fef::FieldType::INDEX &&
            field->collection() == CollectionType::SINGLE)
        {
            FeatureNameBuilder fnb;
            fnb.baseName(getBaseName()).parameter(field->name());
            if (field->isFilter()) {
                visitor.visitDumpFeature(fnb.buildName());
                visitor.visitDumpFeature(fnb.output("completeness").buildName());
                visitor.visitDumpFeature(fnb.output("queryCompleteness").buildName());
                visitor.visitDumpFeature(fnb.output("weight").buildName());
                visitor.visitDumpFeature(fnb.output("matches").buildName());
                visitor.visitDumpFeature(fnb.output("degradedMatches").buildName());
            } else {
                visitor.visitDumpFeature(fnb.buildName());
                visitor.visitDumpFeature(fnb.output("proximity").buildName());
                visitor.visitDumpFeature(fnb.output("completeness").buildName());
                visitor.visitDumpFeature(fnb.output("queryCompleteness").buildName());
                visitor.visitDumpFeature(fnb.output("fieldCompleteness").buildName());
                visitor.visitDumpFeature(fnb.output("orderness").buildName());
                visitor.visitDumpFeature(fnb.output("relatedness").buildName());
                visitor.visitDumpFeature(fnb.output("earliness").buildName());
                visitor.visitDumpFeature(fnb.output("longestSequenceRatio").buildName());
                visitor.visitDumpFeature(fnb.output("segmentProximity").buildName());
                visitor.visitDumpFeature(fnb.output("unweightedProximity").buildName());
                visitor.visitDumpFeature(fnb.output("absoluteProximity").buildName());
                visitor.visitDumpFeature(fnb.output("occurrence").buildName());
                visitor.visitDumpFeature(fnb.output("absoluteOccurrence").buildName());
                visitor.visitDumpFeature(fnb.output("weightedOccurrence").buildName());
                visitor.visitDumpFeature(fnb.output("weightedAbsoluteOccurrence").buildName());
                visitor.visitDumpFeature(fnb.output("significantOccurrence").buildName());
                visitor.visitDumpFeature(fnb.output("weight").buildName());
                visitor.visitDumpFeature(fnb.output("significance").buildName());
                visitor.visitDumpFeature(fnb.output("importance").buildName());
                visitor.visitDumpFeature(fnb.output("segments").buildName());
                visitor.visitDumpFeature(fnb.output("matches").buildName());
                visitor.visitDumpFeature(fnb.output("outOfOrder").buildName());
                visitor.visitDumpFeature(fnb.output("gaps").buildName());
                visitor.visitDumpFeature(fnb.output("gapLength").buildName());
                visitor.visitDumpFeature(fnb.output("longestSequence").buildName());
                visitor.visitDumpFeature(fnb.output("head").buildName());
                visitor.visitDumpFeature(fnb.output("tail").buildName());
                visitor.visitDumpFeature(fnb.output("segmentDistance").buildName());
                visitor.visitDumpFeature(fnb.output("degradedMatches").buildName());
            }
        }
    }
}

Blueprint::UP
FieldMatchBlueprint::createInstance() const
{
    return std::make_unique<FieldMatchBlueprint>();
}

bool
FieldMatchBlueprint::setup(const IIndexEnvironment & env,
                           const ParameterList & params)
{
    _field = params[0].asField();
    _shared_state_key = "fef.fieldmatch." + _field->name();

    const Properties & lst = env.getProperties();
    Property obj;
    obj = lst.lookup(getName(), "proximityLimit");
    if (obj.found()) {
        _params.setProximityLimit(atoi(obj.get().c_str()));
    }
    obj = lst.lookup(getName(), "maxAlternativeSegmentations");
    if (obj.found()) {
        _params.setMaxAlternativeSegmentations(atoi(obj.get().c_str()));
    }
    obj = lst.lookup(getName(), "maxOccurrences");
    if (obj.found()) {
        _params.setMaxOccurrences(atoi(obj.get().c_str()));
    }
    obj = lst.lookup(getName(), "proximityCompletenessImportance");
    if (obj.found()) {
        _params.setProximityCompletenessImportance(vespalib::locale::c::atof(obj.get().c_str()));
    }
    obj = lst.lookup(getName(), "relatednessImportance");
    if (obj.found()) {
        _params.setRelatednessImportance(vespalib::locale::c::atof(obj.get().c_str()));
    }
    obj = lst.lookup(getName(), "earlinessImportance");
    if (obj.found()) {
        _params.setEarlinessImportance(vespalib::locale::c::atof(obj.get().c_str()));
    }
    obj = lst.lookup(getName(), "segmentProximityImportance");
    if (obj.found()) {
        _params.setSegmentProximityImportance(vespalib::locale::c::atof(obj.get().c_str()));
    }
    obj = lst.lookup(getName(), "occurrenceImportance");
    if (obj.found()) {
        _params.setOccurrenceImportance(vespalib::locale::c::atof(obj.get().c_str()));
    }
    obj = lst.lookup(getName(), "fieldCompletenessImportance");
    if (obj.found()) {
        _params.setFieldCompletenessImportance(vespalib::locale::c::atof(obj.get().c_str()));
    }
    obj = lst.lookup(getName(), "proximityTable");
    if (obj.found()) {
        std::vector<feature_t> table;
        for (uint32_t i = 0; i < obj.size(); ++i) {
            table.push_back(vespalib::locale::c::atof(obj.getAt(i).c_str()));
        }
        _params.setProximityTable(table);
    }
    if (!_params.valid()) {
        return false;
    }

    // normalized
    describeOutput("score",
                   "A normalized measure of the degree to which this query and field matched (default, the long name of this is match). Use "
                   "this if you don't want to create your own combination function of more fine grained fieldmatch features.");
    describeOutput("proximity",
                   "Normalized proximity - a value which is close to 1 when matched terms are close inside each segment, and close to zero "
                   "when they are far apart inside segments. Relatively more connected terms influence this value more. This is "
                   "absoluteProximity/average connectedness for the query terms for this field.");
    describeOutput("completeness",
                   "The normalized total completeness, where field completeness is more important.");
    describeOutput("queryCompleteness",
                   "The normalized ratio of query tokens matched in the field.");
    describeOutput("fieldCompleteness",
                   "The normalized ratio of query tokens which was matched in the field.");
    describeOutput("orderness",
                   "A normalized metric of how well the order of the terms agrees in the chosen segments.");
    describeOutput("relatedness",
                   "A normalized measure of the degree to which different terms are related (occurring in the same segment).");
    describeOutput("earliness",
                   "A normalized measure of how early the first segment occurs in this field.");
    describeOutput("longestSequenceRatio",
                   "A normalized metric of the relative size of the longest sequence.");
    describeOutput("segmentProximity",
                   "A normalized metric of the closeness (inverse of spread) of segments in the field.");
    describeOutput("unweightedProximity",
                   "The normalized proximity of the matched terms, not taking term connectedness into account. This number is close to 1 if "
                   "all the matched terms are following each other in sequence, and close to 0 if they are far from each other or out of "
                   "order.");
    describeOutput("absoluteProximity",
                   "Returns the normalized proximity of the matched terms, weighted by the connectedness of the query terms. This number is "
                   "0.1 if all the matched terms are and have default or lower connectedness, close to 1 if they are following in sequence "
                   "and have a high connectedness, and close to 0 if they are far from each other in the segments or out of order.");
    describeOutput("occurrence",
                   "Returns a normalized measure of the number of occurrence of the terms of the query. This number is 1 if there are many "
                   " occurrences of the query terms in absolute terms, or relative to the total content of the field, and 0 if there are "
                   "none.");
    describeOutput("absoluteOccurrence",
                   "Returns a normalized measure of the number of occurrence of the terms of the query.");
    describeOutput("weightedOccurrence",
                   "Returns a normalized measure of the number of occurrence of the terms of the query, weighted by term weight. This number "
                   "is close to 1 if there are many occurrences of highly weighted query terms, in absolute terms, or relative to the total "
                   "content of the field, and 0 if there are none.");
    describeOutput("weightedAbsoluteOccurrence",
                   "Returns a normalized measure of the number of occurrence of the terms of the query, taking weights into account so that "
                   "occurrences of higher weighted query terms has more impact than lower weighted terms.");
    describeOutput("significantOccurrence",
                   "Returns a normalized measure of the number of occurrence of the terms of the query in absolute terms, or relative to the "
                   "total content of the field, weighted by term significance.");

    // normalized and relative to the whole query
    describeOutput("weight",
                   "The normalized weight of this match relative to the whole query.");
    describeOutput("significance",
                   "Returns the normalized term significance (1-frequency) of the terms of this match relative to the whole query.");
    describeOutput("importance",
                   "Returns the average of significance and weight. This has the same properties as those metrics.");

    // not normalized
    describeOutput("segments",
                   "The number of field text segments which are needed to match the query as completely as possible.");
    describeOutput("matches",
                   "The number of query terms which was matched in this field.");
    describeOutput("outOfOrder",
                   "The total number of out of order token sequences within matched field segments.");
    describeOutput("gaps",
                   "The total number of position jumps (backward or forward) within field segments.");
    describeOutput("gapLength",
                   "The summed length of all gaps within segments.");
    describeOutput("longestSequence",
                   "The size of the longest matched continuous, in-order sequence in the field.");
    describeOutput("head",
                   "The number of tokens in the field preceeding the start of the first matched segment.");
    describeOutput("tail",
                   "The number of tokens in the field following the end of the last matched segment.");
    describeOutput("segmentDistance",
                   "The sum of the distance between all segments making up a match to the query, measured as the sum of the number of token "
                   "positions separating the start of each field adjacent segment.");
    describeOutput("degradedMatches",
                   "The number of degraded query terms (no position information available) which was matched in this field.");
    return true;
}

FeatureExecutor &
FieldMatchBlueprint::createExecutor(const IQueryEnvironment & env, vespalib::Stash &stash) const
{
    auto *shared_state = dynamic_cast<const FieldMatchExecutorSharedState *>(env.getObjectStore().get(_shared_state_key));
    if (shared_state == nullptr) {
        shared_state = &stash.create<FieldMatchExecutorSharedState>(env, *_field, _params);
    }
    return stash.create<FieldMatchExecutor>(*shared_state);
}

void FieldMatchBlueprint::prepareSharedState(const IQueryEnvironment &env, IObjectStore & store) const {
    if (store.get(_shared_state_key) == nullptr) {
        store.add(_shared_state_key, std::make_unique<FieldMatchExecutorSharedState>(env, *_field, _params));
    }
}

}
