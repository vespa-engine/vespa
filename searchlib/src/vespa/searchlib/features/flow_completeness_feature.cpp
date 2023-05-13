// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flow_completeness_feature.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/util/stash.h>

#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".features.flowcompleteness");

namespace search::features {

//-----------------------------------------------------------------------------

FlowCompletenessExecutor::FlowCompletenessExecutor(const fef::IQueryEnvironment &env,
                                                   const FlowCompletenessParams &params)
    : _params(params),
      _terms(),
      _queue(),
      _sumTermWeight(0)
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        LOG(spam, "consider term %u", i);
        const fef::ITermData *termData = env.getTerm(i);
        LOG(spam, "term %u weight %u", i, termData->getWeight().percent());
        if (termData->getWeight().percent() != 0) { // only consider query terms with contribution
            using FRA = fef::ITermFieldRangeAdapter;
            uint32_t j = 0;
            for (FRA iter(*termData); iter.valid(); iter.next()) {
                const fef::ITermFieldData &tfd = iter.get();
                LOG(spam, "term %u field data %u for field id %u (my field id %u)",
                    i, j++, tfd.getFieldId(), _params.fieldId);
                if (tfd.getFieldId() == _params.fieldId) {
                    int termWeight = termData->getWeight().percent();
                    _sumTermWeight += termWeight;
                    _terms.push_back(Term(tfd.getHandle(), termWeight));
                }
            }
        }
    }
    LOG(spam, "added %zu terms", _terms.size());
}

namespace {
using TermIdxList = std::vector<uint32_t>;
using PosList = std::vector<uint32_t>;

using TermIdxMap = vespalib::hash_map<uint32_t, uint32_t>;

struct State {
    int       elementWeight;
    uint32_t  elementLength;
    uint32_t  matchedTerms;
    int       sumTermWeight;

    std::vector<PosList> positionsForTerm;
    uint32_t             posLimit;
    PosList              matchedPosForTerm;
    TermIdxMap           matchedTermForPos; // maps pos -> term

    double    score;
    double    flow;
    feature_t completeness;
    feature_t fieldCompleteness;
    feature_t queryCompleteness;

    State(int weight, uint32_t length)
        : elementWeight(weight), elementLength(length),
          matchedTerms(0), sumTermWeight(0),
          posLimit(0),
          score(0.0), flow(0.0),
          completeness(0.0), fieldCompleteness(0.0), queryCompleteness(0.0) {}
    ~State() { }

    void addMatch(int termWeight) {
        ++matchedTerms;
        sumTermWeight += termWeight;
    }

    struct Path {
        std::vector<uint32_t> path;
        bool operator< (const Path& other) const {
            return path.size() < other.path.size();
        }
    };

    Path bfs(vespalib::PriorityQueue<Path> &queue)
    {
        TermIdxList seen(matchedTerms, 0);
        while (!queue.empty()) {
            Path firstP = queue.front();
            queue.pop_front();
            uint32_t startTerm = firstP.path.back();
            seen[startTerm] = 1;
            PosList &edges = positionsForTerm[startTerm];
            for (size_t j = 0; j < edges.size(); ++j) {
                Path nextP = firstP;
                uint32_t pos = edges[j];
                nextP.path.push_back(pos);
                TermIdxMap::const_iterator it = matchedTermForPos.find(pos);
                if (it == matchedTermForPos.end()) {
                    return nextP;
                } else {
                    uint32_t nextTerm = it->second;
                    if (seen[nextTerm] == 0) {
                        seen[nextTerm] = 1;
                        nextP.path.push_back(nextTerm);
                        queue.push(nextP);
                    }
                }
            }
        }
        return Path();
    }

    int findMatches() {
        vespalib::PriorityQueue<Path> q;

        for (size_t i = 0; i < matchedTerms; ++i) {
            if (matchedPosForTerm[i] == IllegalPosId) {
                Path p;
                p.path.push_back(i);
                q.push(p);
            }
        }
        if (q.empty()) {
            return 0;
        }
        Path p = bfs(q);
        if (p.path.size() == 0) {
            return 0;
        }
        while (p.path.size() > 1) {
            uint32_t pos = p.path.back();
            assert(pos < posLimit);
            p.path.pop_back();
            uint32_t tix = p.path.back();
            assert(tix < matchedTerms);
            p.path.pop_back();
            matchedTermForPos[pos] = tix;
            matchedPosForTerm[tix] = pos;
        }
        assert(p.path.size() == 0);
        return 1;
    }

    int findSimpleMatches() {
        int found = 0;
        for (size_t tix = 0; tix < matchedTerms; ++tix) {
            assert(matchedPosForTerm[tix] == IllegalPosId);
            assert(positionsForTerm[tix].size() > 0);
            uint32_t pos = positionsForTerm[tix][0];
            assert(pos < posLimit);

            TermIdxMap::const_iterator it = matchedTermForPos.find(pos);
            if (it == matchedTermForPos.end()) {
                ++found;
                matchedTermForPos[pos] = tix;
                matchedPosForTerm[tix] = pos;
            }
        }
        return found;
    }

    void calculateScore(uint32_t queryTerms, double factor) {
        matchedPosForTerm.resize(matchedTerms, IllegalPosId);
        int more = findSimpleMatches();
        flow += more;
        while ((more = findMatches()) > 0) {
            flow += more;
        }
        queryCompleteness = (flow / (double)queryTerms);
        fieldCompleteness = (flow / (double)elementLength);
        completeness = (fieldCompleteness * factor) +
                       (queryCompleteness * (1 - factor));
        score = completeness * (double)sumTermWeight;
    }
};

}


void
FlowCompletenessExecutor::execute(uint32_t)
{
    assert(_queue.empty());
    for (size_t i = 0; i < _terms.size(); ++i) {
        const fef::TermFieldMatchData *tfmd = _md->resolveTermField(_terms[i].termHandle);
        Item item(i, tfmd->begin(), tfmd->end());
        LOG(spam, "found tfmd item with %zu positions", (item.end - item.pos));
        if (item.pos != item.end) {
            _queue.push(item);
        }
    }
    State best(0, 0);
    while (!_queue.empty()) {
        Item &start = _queue.front();
        uint32_t elementId = start.elemId;
        LOG_ASSERT(start.pos != start.end);
        State state(start.pos->getElementWeight(), start.pos->getElementLen());

        while (!_queue.empty() && _queue.front().elemId == elementId) {
            Item &item = _queue.front();

            // update state
            state.positionsForTerm.push_back(PosList());
            while (item.pos != item.end && item.pos->getElementId() == elementId) {
                uint32_t pos = item.pos->getPosition();
                state.positionsForTerm.back().push_back(pos);
                state.posLimit = std::max(state.posLimit, pos + 1);
                ++item.pos;
            }
            state.addMatch(_terms[item.termIdx].termWeight);

            // adjust item and its place in queue
            if (item.pos == item.end) {
                _queue.pop_front();
            } else {
                item.elemId = item.pos->getElementId();
                _queue.adjust();
            }
        }
        state.calculateScore(_terms.size(), _params.fieldCompletenessImportance);
        if (state.score > best.score) {
            best = state;
        }
    }
    outputs().set_number(0, best.completeness);
    outputs().set_number(1, best.fieldCompleteness);
    outputs().set_number(2, best.queryCompleteness);
    outputs().set_number(3, best.elementWeight);
    outputs().set_number(4, _params.fieldWeight);
    outputs().set_number(5, best.flow);

}

void
FlowCompletenessExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _md = &md;
}

//-----------------------------------------------------------------------------

FlowCompletenessBlueprint::FlowCompletenessBlueprint()
    : Blueprint("flowCompleteness"),
      _output(),
      _params()
{
    _output.push_back("completeness");
    _output.push_back("fieldCompleteness");
    _output.push_back("queryCompleteness");
    _output.push_back("elementWeight");
    _output.push_back("weight");
    _output.push_back("flow");
}

void
FlowCompletenessBlueprint::visitDumpFeatures(const fef::IIndexEnvironment &env,
                                             fef::IDumpFeatureVisitor &visitor) const
{
#ifdef notyet
    for (uint32_t i = 0; i < env.getNumFields(); ++i) {
        const fef::FieldInfo &field = *env.getField(i);
        if (field.type() == fef::FieldType::INDEX) {
            if (!field.isFilter()) {
                fef::FeatureNameBuilder fnb;
                fnb.baseName(getBaseName()).parameter(field.name());
                for (size_t out = 0; out < _output.size(); ++out) {
                    visitor.visitDumpFeature(fnb.output(_output[out]).buildName());
                }
            }
        }
    }
#else
    (void)env;
    (void)visitor;
#endif
}

fef::Blueprint::UP
FlowCompletenessBlueprint::createInstance() const
{
    return std::make_unique<FlowCompletenessBlueprint>();
}

bool
FlowCompletenessBlueprint::setup(const fef::IIndexEnvironment &env,
                                 const fef::ParameterList &params)
{
    const fef::FieldInfo *field = params[0].asField();

    _params.fieldId = field->id();
    const fef::Properties &lst = env.getProperties();
    fef::Property obj = lst.lookup(getName(), "fieldCompletenessImportance");
    if (obj.found()) {
        _params.fieldCompletenessImportance = vespalib::locale::c::atof(obj.get().c_str());
    }
    _params.fieldWeight = fef::indexproperties::FieldWeight::lookup(lst, field->name());

    describeOutput(_output[0], "combined completeness for best scored element");
    describeOutput(_output[1], "best scored element completeness");
    describeOutput(_output[2], "query completeness for best scored element");
    describeOutput(_output[3], "element weight of best scored element");
    describeOutput(_output[4], "field weight");
    describeOutput(_output[5], "query terms matching in best element (measured by flow)");
    return true;
}

fef::FeatureExecutor &
FlowCompletenessBlueprint::createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    return stash.create<FlowCompletenessExecutor>(env, _params);
}

}
