// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search::features {

/**
 * Executor for the BM25 ranking algorithm over a single index field.
 */
class Bm25Executor : public fef::FeatureExecutor {
private:
    struct QueryTerm {
        fef::TermFieldHandle handle;
        const fef::TermFieldMatchData* tfmd;
        double idf_mul_k1_plus_one;
        QueryTerm(fef::TermFieldHandle handle_, double inverse_doc_freq, double k1_param) noexcept
            : handle(handle_),
              tfmd(nullptr),
              idf_mul_k1_plus_one(inverse_doc_freq * (k1_param + 1))
        {}
    };

    using QueryTermVector = std::vector<QueryTerm>;

    QueryTermVector _terms;
    double _avg_field_length;

    // The 'k1' param determines term frequency saturation characteristics.
    // The 'b' param adjusts the effects of the field length of the document matched compared to the average field length.
    double _k1_mul_b;
    double _k1_mul_one_minus_b;

public:
    Bm25Executor(const fef::FieldInfo& field,
                 const fef::IQueryEnvironment& env,
                 double avg_field_length,
                 double k1_param,
                 double b_param);

    double static calculate_inverse_document_frequency(uint32_t matching_doc_count, uint32_t total_doc_count);

    void handle_bind_match_data(const fef::MatchData& match_data) override;
    void execute(uint32_t docId) override;
};


/**
 * Blueprint for the BM25 ranking algorithm over a single index field.
 */
class Bm25Blueprint : public fef::Blueprint {
private:
    const fef::FieldInfo* _field;
    double _k1_param;
    double _b_param;

    bool lookup_param(const fef::Properties& props, const vespalib::string& param, double& result) const;

public:
    Bm25Blueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY);
    }
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    void prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
};

}
