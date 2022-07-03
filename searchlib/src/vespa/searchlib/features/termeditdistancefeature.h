// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements a cell class for the cost table constructed when running the term edit distance calculator. This is
 * necessary to keep track of the route actually chosen through the table, since the algorithm itself merely find the
 * minimum cost.
 */
class TedCell {
public:
    TedCell() noexcept;
    TedCell(feature_t cost, uint32_t numDel, uint32_t numIns, uint32_t numSub) noexcept;

    feature_t cost;   // The cost at this point.
    uint32_t  numDel; // The number of deletions to get here.
    uint32_t  numIns; // The number of insertions to get here.
    uint32_t  numSub; // The number of substitutions to get here.
};

/**
 * Implements the necessary config for the term edit distance calculator. This class exists so that the executor does
 * not need a separate copy of the config parsed by the blueprint, and at the same time avoiding that the executor needs
 * to know about the blueprint.
 */
struct TermEditDistanceConfig {
    TermEditDistanceConfig();

    uint32_t  fieldId;    // The id of field to process.
    uint32_t  fieldBegin; // The first field term to evaluate.
    uint32_t  fieldEnd;   // The last field term to evaluate.
    feature_t costDel;    // The cost of a delete.
    feature_t costIns;    // The cost of an insert.
    feature_t costSub;    // The cost of a substitution.
};

/**
 * Implements the executor for the term edit distance calculator.
 */
class TermEditDistanceExecutor : public fef::FeatureExecutor {
public:
    /**
     * Constructs a new executor for the term edit distance calculator.
     *
     * @param config The config for this executor.
     */
    TermEditDistanceExecutor(const fef::IQueryEnvironment &env,
                             const TermEditDistanceConfig &config);


    /**
     *
     * This executor prepares a matrix that has one row per query term, and one column per field term. Initialize this
     * array as follows:
     *
     *   |f i e l d
     *  -+---------
     *  q|0 1 2 3 4
     *  u|1 . . . .
     *  e|2 . . . .
     *  r|3 . . . .
     *  y|4 . . . .
     *
     * Run through this matrix per field term, per query term; i.e. column by column, row by row. Compare the field term
     * at that column with the query term at that row. Then set the value of that cell to the minimum of:
     *
     * 1. The cost of substitution; the above-left value plus the cost (0 if equal).
     * 2. The cost of insertion; the left value plus the cost.
     * 3. The cost of deletion; the above value plus the cost.
     *
     * After completing the matrix, the minimum cost is contained in the bottom-right.
     *
     * @param docid local document id to be evaluated
     */
    void execute(uint32_t docId) override;

private:
    /**
     * Writes the given list of feature values to log so that it can be viewed for instrumentation.
     *
     * @param row     The list of feature values to write.
     * @param numCols The number of columns to write.
     */
    void logRow(const std::vector<TedCell> &row, size_t numCols);

    void handle_bind_match_data(const fef::MatchData &md) override;

private:
    const TermEditDistanceConfig             &_config;       // The config for this executor.
    std::vector<fef::TermFieldHandle> _fieldHandles; // The handles of all query terms.
    std::vector<feature_t>                    _termWeights;  // The weights of all query terms.
    std::vector<TedCell>                      _prevRow;      // Optimized representation of the cost table.
    std::vector<TedCell>                      _thisRow;      //
    const fef::MatchData                     *_md;
};

/**
 * Implements the blueprint for the term edit distance calculator.
 */
class TermEditDistanceBlueprint : public fef::Blueprint {
public:
    /**
     * Constructs a new blueprint for the term edit distance calculator.
     */
    TermEditDistanceBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::SINGLE);
    }

    /**
     * The cost of each operation is specified by the parameters to the {@link #setup} method of this blueprint. All
     * costs are multiplied by the relative weight of eacht query term. Furthermore, if the query term is not mandatory,
     * all operations are free. The parameters are:
     *
     * 1. The name of the field to calculate the distance for.
     * 2. The cost of ignoring a query term, this is typically HIGH.
     * 3. The cost of inserting a field term into the query term, this is typically LOW.
     * 4. The cost of substituting a field term with a query term, this is also typically LOW.
     * 5. Optional: The field position to begin iteration.
     * 6. Optional: The field position to end iteration.
     *
     * @param env    The index environment.
     * @param params A list of the parameters mentioned above.
     * @return Whether or not setup was possible.
     */
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;

private:
    TermEditDistanceConfig _config; // The config for this blueprint.
};

}
