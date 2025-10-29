// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querynodes.h"
#include "termdatafromnode.h"
#include "viewresolver.h"
#include "handlerecorder.h"
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/classname.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.querynodes");

using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::IIndexEnvironment;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::query::Node;
using std::string;
using vespalib::Issue;

namespace proton::matching {

ProtonTermData::ProtonTermData() noexcept = default;
ProtonTermData::~ProtonTermData() = default;

namespace {

bool
is_attribute(FieldType type) noexcept {
    return (type == FieldType::ATTRIBUTE) || (type == FieldType::HIDDEN_ATTRIBUTE);
}

}

void
ProtonTermData::propagate_document_frequency(uint32_t matching_doc_count, uint32_t total_doc_count)
{
    for (size_t i = 0; i < _fields.size(); ++i) {
        _fields[i].setDocFreq(matching_doc_count, total_doc_count);
    }
}

void
ProtonTermData::resolve(const ViewResolver &resolver, const IIndexEnvironment &idxEnv,
                        const string &view, bool forceFilter)
{
    std::vector<string> fields;
    resolver.resolve(((view == "") ? "default" : view), fields);
    _fields.clear();
    _fields.reserve(fields.size());
    for (size_t i = 0; i < fields.size(); ++i) {
        const FieldInfo *info = idxEnv.getFieldByName(fields[i]);
        if (info != nullptr) {
            _fields.emplace_back(fields[i], info->id(),
                                (forceFilter ? search::fef::FilterThreshold(true) : info->get_filter_threshold()));
            FieldEntry & field = _fields.back();
            field.attribute_field = is_attribute(info->type());
        } else {
            LOG(debug, "ignoring undefined field: '%s'", fields[i].c_str());
        }
    }
}

void
ProtonTermData::resolveFromChildren(const std::vector<Node *> &subterms)
{
    for (size_t i = 0; i < subterms.size(); ++i) {
        const ProtonTermData *child = termDataFromNode(*subterms[i]);
        if (child == nullptr) {
            Issue::report("child of equiv is not a term");
            continue;
        }
        for (size_t j = 0; j < child->numFields(); ++j) {
            const FieldEntry & subSpec = child->field(j);
            if (lookupField(subSpec.getFieldId()) == nullptr) {
                // this must happen before handles are reserved
                LOG_ASSERT(subSpec.getHandle() == search::fef::IllegalHandle);
                _fields.emplace_back(subSpec._field_spec.getName(), subSpec.getFieldId(), false);
            }
        }
    }
}

void
ProtonTermData::allocateTerms(MatchDataLayout &mdl)
{
    for (size_t i = 0; i < _fields.size(); ++i) {
        _fields[i]._field_spec.setHandle(mdl.allocTermField(_fields[i].getFieldId()));
    }
}

void
ProtonTermData::setDocumentFrequency(uint32_t estHits, uint32_t docIdLimit)
{
    if (docIdLimit > 1) {
        uint32_t total_doc_count = docIdLimit - 1;
        propagate_document_frequency(std::min(estHits, total_doc_count), total_doc_count);
    } else {
        propagate_document_frequency(0, 1);
    }
}

void ProtonTermData::useFieldEntry(const FieldEntry &source) {
    _fields.clear();
    _fields.emplace_back(source.getName(), source.getFieldId(), source._field_spec.get_filter_threshold());
    FieldEntry &target = _fields.back();
    target._field_spec = source._field_spec;
    target.attribute_field = source.attribute_field;
    target.setDocFreq(source.get_matching_doc_count(), source.get_total_doc_count());
}

const ProtonTermData::FieldEntry *
ProtonTermData::lookupField(uint32_t fieldId) const
{
    for (size_t i = 0; i < _fields.size(); ++i) {
        if (_fields[i].getFieldId() == fieldId) {
            return &_fields[i];
        }
    }
    return nullptr;
}

TermFieldHandle
ProtonTermData::FieldEntry::getHandle(MatchDataDetails requested_details) const
{
    TermFieldHandle handle(_field_spec.getHandle());
    HandleRecorder::register_handle(handle, requested_details);
    return handle;
}

template <typename Base>
search::queryeval::FieldSpec ProtonTermWithFields<Base>::inner_field_spec(const search::queryeval::FieldSpec& parentSpec) const {
    std::string me = vespalib::getClassName(*this);
    LOG(debug, "ProtonTerm[%s] inner_field_spec[%d] check my %zd fields", me.c_str(), parentSpec.getFieldId(), numFields());
    for (size_t i = 0; i < numFields(); i++) {
        const auto& f = field(i);
        if (f._field_spec.getFieldId() == parentSpec.getFieldId()) {
            LOG(debug, "found my field with handle %d\n", f._field_spec.getHandle());
            return f._field_spec;
        }
    }
    LOG(debug, "inner_field_spec: no match for field id=%d in my %zd fields", parentSpec.getFieldId(), numFields());
    return search::query::Term::inner_field_spec(parentSpec);
}

template struct ProtonTerm<search::query::LocationTerm>;
template struct ProtonTerm<search::query::NumberTerm>;
template struct ProtonTerm<search::query::Phrase>;
template struct ProtonTerm<search::query::PrefixTerm>;
template struct ProtonTerm<search::query::RangeTerm>;
template struct ProtonTerm<search::query::StringTerm>;
template struct ProtonTerm<search::query::SubstringTerm>;
template struct ProtonTerm<search::query::SuffixTerm>;
template struct ProtonTerm<search::query::WeightedSetTerm>;
template struct ProtonTerm<search::query::DotProduct>;
template struct ProtonTerm<search::query::WandTerm>;
template struct ProtonTerm<search::query::PredicateQuery>;
template struct ProtonTerm<search::query::RegExpTerm>;
template struct ProtonTerm<search::query::FuzzyTerm>;
template struct ProtonTerm<search::query::InTerm>;
template struct ProtonTerm<search::query::WordAlternatives>;

ProtonEquiv::~ProtonEquiv() = default;
ProtonWordAlternatives::~ProtonWordAlternatives() = default;
ProtonSameElement::~ProtonSameElement() = default;
ProtonNearestNeighborTerm::~ProtonNearestNeighborTerm() = default;

namespace {

std::vector<std::unique_ptr<search::query::StringTerm>>
upcast_proton_children(std::vector<std::unique_ptr<ProtonStringTerm>> children)
{
    std::vector<std::unique_ptr<search::query::StringTerm>> base_children;
    base_children.reserve(children.size());
    for (auto& child : children) {
        base_children.emplace_back(std::move(child));
    }
    return base_children;
}

std::vector<std::unique_ptr<search::query::StringTerm>>
convert_from_term_vector(const search::query::TermVector& terms, const std::string & view)
{
    std::vector<std::unique_ptr<search::query::StringTerm>> proton_children;
    proton_children.reserve(terms.size());
    for (uint32_t i = 0; i < terms.size(); i++) {
        auto pair = terms.getAsString(i);
        std::string word(pair.first);
        auto tp = std::make_unique<ProtonStringTerm>(word, view, 0, pair.second);
        proton_children.emplace_back(std::move(tp));
    }
    return proton_children;
}

}

ProtonWordAlternatives::ProtonWordAlternatives(std::vector<std::unique_ptr<search::query::StringTerm>> children,
                                               const std::string & view, int32_t id, search::query::Weight weight)
  : ProtonTermWithFields(std::move(children), view, id, weight)
{
}

ProtonWordAlternatives::ProtonWordAlternatives(std::vector<std::unique_ptr<ProtonStringTerm>> children,
                                               const std::string & view, int32_t id, search::query::Weight weight)
  : ProtonTermWithFields(upcast_proton_children(std::move(children)), view, id, weight)
{
}

ProtonWordAlternatives::ProtonWordAlternatives(std::unique_ptr<search::query::TermVector> terms,
                                               const std::string & view, int32_t id, search::query::Weight weight)
  : ProtonTermWithFields(convert_from_term_vector(*terms, view), view, id, weight)
{
}

}
