// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_ctx_builder.h"
#include <vespa/searchlib/attribute/attribute_blueprint_factory.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>

using namespace search::attribute;
using namespace search::attribute::test;

namespace search::queryeval::test {

namespace {

template <typename AttributeType, bool is_string, bool is_multivalue>
void
populate_attribute(AttributeType& attr, uint32_t docid_limit, const HitSpecs& hit_specs)
{
    for (auto spec : hit_specs) {
        auto docids = random_docids(docid_limit, spec.num_hits);
        docids->foreach_truebit([&](uint32_t docid) {
            if constexpr (is_string) {
                if constexpr (is_multivalue) {
                    attr.append(docid, std::to_string(spec.term_value), random_int(1, 100));
                } else {
                    attr.update(docid, std::to_string(spec.term_value));
                }
            } else {
                if constexpr (is_multivalue) {
                    attr.append(docid, spec.term_value, random_int(1, 100));
                } else {
                    attr.update(docid, spec.term_value);
                }
            }
        });
    }
}

AttributeVector::SP
make_attribute(const Config& cfg, vespalib::stringref field_name, uint32_t num_docs, const HitSpecs& hit_specs)
{
    auto attr = AttributeFactory::createAttribute(field_name, cfg);
    attr->addReservedDoc();
    attr->addDocs(num_docs);
    uint32_t docid_limit = attr->getNumDocs();
    assert(docid_limit == (num_docs + 1));
    bool is_multivalue = cfg.collectionType() != CollectionType::SINGLE;
    if (attr->isStringType()) {
        auto& real = dynamic_cast<StringAttribute&>(*attr);
        if (is_multivalue) {
            populate_attribute<StringAttribute, true, true>(real, docid_limit, hit_specs);
        } else {
            populate_attribute<StringAttribute, true, false>(real, docid_limit, hit_specs);
        }
    } else {
        auto& real = dynamic_cast<IntegerAttribute&>(*attr);
        if (is_multivalue) {
            populate_attribute<IntegerAttribute, false, true>(real, docid_limit, hit_specs);
        } else {
            populate_attribute<IntegerAttribute, false, false>(real, docid_limit, hit_specs);
        }
    }
    attr->commit(true);
    return attr;
}

class AttributeSearchable : public BenchmarkSearchable {
private:
    std::unique_ptr<MockAttributeContext> _attr_ctx;

public:
    AttributeSearchable(std::unique_ptr<MockAttributeContext> attr_ctx) : _attr_ctx(std::move(attr_ctx)) {}
    std::unique_ptr<Blueprint> create_blueprint(const FieldSpec& field_spec,
                                                const search::query::Node& term) override {
        AttributeBlueprintFactory factory;
        FakeRequestContext req_ctx(_attr_ctx.get());
        return factory.createBlueprint(req_ctx, field_spec, term);
    }
};

}

AttributeContextBuilder::AttributeContextBuilder()
    : _ctx(std::make_unique<MockAttributeContext>())
{
}

void
AttributeContextBuilder::add(const search::attribute::Config& cfg, vespalib::stringref field_name, uint32_t num_docs, const HitSpecs& hit_specs)
{
    auto attr = make_attribute(cfg, field_name, num_docs, hit_specs);
    _ctx->add(std::move(attr));
}

std::unique_ptr<BenchmarkSearchable>
AttributeContextBuilder::build()
{
    return std::make_unique<AttributeSearchable>(std::move(_ctx));
}

}
