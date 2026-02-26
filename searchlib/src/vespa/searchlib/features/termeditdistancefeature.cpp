// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termeditdistancefeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/util/stash.h>


#include <vespa/log/log.h>
LOG_SETUP(".features.termeditdistance");

namespace search::features {

//---------------------------------------------------------------------------------------------------------------------
// TedCell
//---------------------------------------------------------------------------------------------------------------------
TedCell::TedCell() noexcept :
    cost(util::FEATURE_MAX),
    numDel(0),
    numIns(0),
    numSub(0)
{}

TedCell::TedCell(feature_t argCost, uint32_t argNumDel, uint32_t argNumIns, uint32_t argNumSub) noexcept :
    cost(argCost),
    numDel(argNumDel),
    numIns(argNumIns),
    numSub(argNumSub)
{}

//---------------------------------------------------------------------------------------------------------------------
// TermEditDistanceConfig
//---------------------------------------------------------------------------------------------------------------------
TermEditDistanceConfig::TermEditDistanceConfig() :
    fieldId(search::fef::IllegalHandle),
    fieldBegin(0),
    fieldEnd(std::numeric_limits<uint32_t>::max()),
    costDel(1),
    costIns(1),
    costSub(1)
{}

//---------------------------------------------------------------------------------------------------------------------
// TermEditDistanceExecutor
//---------------------------------------------------------------------------------------------------------------------
TermEditDistanceExecutor::TermEditDistanceExecutor(const search::fef::IQueryEnvironment &env,
                                                   const TermEditDistanceConfig &config) :
    search::fef::FeatureExecutor(),
    _config(config),
    _fieldHandles(),
    _termWeights(),
    _prevRow(16),
    _thisRow(_prevRow.size()),
    _md(nullptr)
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        _fieldHandles.push_back(util::getTermFieldHandle(env, i, config.fieldId));
        _termWeights.push_back(1.0f);

        // XXX was intended to use something like this instead of 1.0f:
        // const search::fef::TermData& term = *env.getTerm(i);
        // term.isMandatory() ? (feature_t)term.getWeight() : 0.0f
    }
}

void
TermEditDistanceExecutor::execute(uint32_t docId)
{
    // Determine the number of terms in the field.
    uint32_t numQueryTerms = _fieldHandles.size();
    uint32_t fieldBegin    = _config.fieldBegin;
    uint32_t fieldEnd      = std::min(_config.fieldEnd,
                                      (uint32_t)inputs().get_number(0));

    // _P_A_R_A_N_O_I_A_
    TedCell last;
    if (fieldBegin < fieldEnd) {
        // Construct the cost table.
        uint32_t numFieldTerms = fieldEnd - fieldBegin;
        if (_prevRow.size() < numFieldTerms + 1) {
            _prevRow.resize(numFieldTerms + 1);
            _thisRow.resize(_prevRow.size());
        }
        for (uint32_t field = 0; field <= numFieldTerms; ++field) {
            _prevRow[field] = TedCell(field * _config.costIns, 0, field, 0);
        }
        //LOG(debug, "[   F     I     E     L     D     S   ]");
        //logRow(_prevRow, numFieldTerms + 1);

        // Iterate over each query term.
        for (uint32_t query = 1; query <= numQueryTerms; ++query) {
            search::fef::FieldPositionsIterator it; // this is not vaild

            // Look for a match of this term.
            search::fef::TermFieldHandle handle = _fieldHandles[query - 1];
            if (handle != search::fef::IllegalHandle) {
                const fef::TermFieldMatchData &tfmd = *_md->resolveTermField(handle);
                if (tfmd.has_ranking_data(docId)) {
                    it = tfmd.getIterator(); // this is now valid
                    while (it.valid() && it.getPosition() < fieldBegin) {
                        it.next(); // forward to window
                    }
                }
            }

            // Predefine the cost of operations on the current term.
            feature_t weight  = _termWeights[query - 1];
            feature_t costDel = _config.costDel * weight;
            feature_t costIns = _config.costIns * weight;
            feature_t costSub = _config.costSub * weight;

            // Iterate over each field term.
            _thisRow[0] = TedCell(_prevRow[0].cost + costDel, query, 0, 0);
            for (uint32_t field = 1; field <= numFieldTerms; ++field) {
                // If the iterator is still valid, we _might_ have a match.
                if (it.valid()) {
                    // If the iterator knows an occurance at this field term, this is a match.
                    if (it.getPosition() == fieldBegin + (field - 1)) {
                        _thisRow[field] = _prevRow[field - 1]; // no cost
                        it.next();
                        continue; // skip calculations
                    }
                }

                // Determine the least-cost operation.
                feature_t del = _prevRow[field    ].cost + costDel; // cost per previous query term, ie. ignoring this query term.
                feature_t ins = _thisRow[field - 1].cost + costIns; // cost per previous field term, ie. insert this query term.
                feature_t sub = _prevRow[field - 1].cost + costSub; // cost to replace field term with query term.

                feature_t min = std::min(del, std::min(ins, sub));
                if (min == del) {
                    const TedCell &cell = _prevRow[field];
                    _thisRow[field] = TedCell(del, cell.numDel + 1, cell.numIns, cell.numSub);
                }
                else if(min == ins) {
                    const TedCell &cell = _thisRow[field - 1];
                    _thisRow[field] = TedCell(ins, cell.numDel, cell.numIns + 1, cell.numSub);
                }
                else {
                    const TedCell &cell = _prevRow[field - 1];
                    _thisRow[field] = TedCell(sub, cell.numDel, cell.numIns, cell.numSub + 1);
                }
            }
            _thisRow.swap(_prevRow);
            //logRow(_prevRow, numFieldTerms + 1);
        }

        // Retrieve the bottom-right value.
        last = _prevRow[numFieldTerms];
    }
    outputs().set_number(0, last.cost);
    outputs().set_number(1, last.numDel);
    outputs().set_number(2, last.numIns);
    outputs().set_number(3, last.numSub);
}

void
TermEditDistanceExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _md = &md;
}

void
TermEditDistanceExecutor::logRow(const std::vector<TedCell> &row, size_t numCols)
{
    if (LOG_WOULD_LOG(info)) {
        std::string str = "[ ";
        for (size_t i = 0; i < numCols; ++i) {
            str.append(vespalib::make_string("%5.2f", row[i].cost));
            if (i < numCols - 1) {
                str.append(" ");
            }
        }
        str.append(" ]");
        LOG(debug, "%s", str.c_str());
    }
}

//---------------------------------------------------------------------------------------------------------------------
// TermEditDistanceBlueprint
//---------------------------------------------------------------------------------------------------------------------
TermEditDistanceBlueprint::TermEditDistanceBlueprint() :
    search::fef::Blueprint("termEditDistance"),
    _config()
{
    // empty
}

void
TermEditDistanceBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                             search::fef::IDumpFeatureVisitor &) const
{
    // empty
}

bool
TermEditDistanceBlueprint::setup(const search::fef::IIndexEnvironment &env,
                                 const search::fef::ParameterList &params)
{
    _config.fieldId = params[0].asField()->id();

    std::string costDel = env.getProperties().lookup(getName(), "costDel").getAt(0);
    _config.costDel = costDel.empty() ? 1.0f : vespalib::locale::c::atof(costDel.c_str());
    std::string costIns = env.getProperties().lookup(getName(), "costIns").getAt(0);
    _config.costIns = costIns.empty() ? 1.0f : vespalib::locale::c::atof(costIns.c_str());
    std::string costSub = env.getProperties().lookup(getName(), "costSub").getAt(0);
    _config.costSub = costSub.empty() ? 1.0f : vespalib::locale::c::atof(costSub.c_str());

    defineInput(vespalib::make_string("fieldLength(%s)", params[0].getValue().c_str()));
    describeOutput("out", "Term-wise edit distance.");
    describeOutput("del", "Number of deletions performed.");
    describeOutput("ins", "Number of insertions performed.");
    describeOutput("sub", "Number of substitutions performed.");
    return true;
}

search::fef::Blueprint::UP
TermEditDistanceBlueprint::createInstance() const
{
    return std::make_unique<TermEditDistanceBlueprint>();
}

search::fef::FeatureExecutor &
TermEditDistanceBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    return stash.create<TermEditDistanceExecutor>(env, _config);
}

}
