// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniperdfw.h"
#include "docsumwriter.h"
#include "docsumstate.h"
#include "i_docsum_store_document.h"
#include "keywordextractor.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/queryeval/split_float.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <vespa/juniper/config.h>
#include <vespa/juniper/queryhandle.h>
#include <vespa/juniper/query_item.h>
#include <vespa/juniper/result.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.dynamicteaserdfw");

namespace search::docsummary {

struct ExplicitItemData
{
    vespalib::stringref _index;
    int32_t _weight;

    ExplicitItemData()
        : _index(), _weight(0)
    {}
};



/**
 * This struct is used to point to the traversal state located on
 * the stack of the IQuery Traverse method. This is needed because
 * the Traverse method is const.
 **/
class JuniperDFWQueryItem : public juniper::QueryItem
{
    search::SimpleQueryStackDumpIterator *_si;
    const ExplicitItemData *_data;
public:
    JuniperDFWQueryItem() : _si(nullptr), _data(nullptr) {}
    ~JuniperDFWQueryItem() override = default;
    explicit JuniperDFWQueryItem(search::SimpleQueryStackDumpIterator *si) : _si(si), _data(nullptr) {}
    explicit JuniperDFWQueryItem(const ExplicitItemData *data) : _si(nullptr), _data(data) {}
    JuniperDFWQueryItem(const QueryItem&) = delete;
    JuniperDFWQueryItem& operator= (const QueryItem&) = delete;

    vespalib::stringref get_index() const override;
    int get_weight() const override;
    juniper::ItemCreator get_creator() const override;
};

vespalib::stringref
JuniperDFWQueryItem::get_index() const
{
    return _si != nullptr ? _si->getIndexName() : _data->_index;
}

int
JuniperDFWQueryItem::get_weight() const
{
    return _si != nullptr ? _si->GetWeight().percent() : _data->_weight;
}

juniper::ItemCreator
JuniperDFWQueryItem::get_creator() const
{
    return _si != nullptr ? _si->getCreator() : juniper::ItemCreator::CREA_ORIG;
}

class TermVisitor : public search::fef::IPropertiesVisitor
{
public:
    juniper::IQueryVisitor *_visitor;
    JuniperDFWQueryItem _item;

    explicit TermVisitor(juniper::IQueryVisitor *visitor)
        : _visitor(visitor),
          _item()
    {}
    void visitProperty(const search::fef::Property::Value &key, const search::fef::Property &values) override;

};

void
TermVisitor::visitProperty(const search::fef::Property::Value &key, const search::fef::Property &values)
{
    ExplicitItemData data;
    JuniperDFWQueryItem item(&data);
    int index = 0;
    int numBlocks = atoi(values.getAt(index++).c_str());
    data._index = key;

    _visitor->VisitAND(&item, numBlocks);

    for (int i = 0; i < numBlocks; i++) {
        const search::fef::Property::Value * s = & values.getAt(index++);
        if ((*s)[0] == '"') {
            s = & values.getAt(index++);
            int phraseLen = atoi(s->c_str());
            _visitor->VisitPHRASE(&item, phraseLen);
            s = & values.getAt(index++);
            while ((*s)[0] != '"') {
                _visitor->VisitKeyword(&item, s->c_str(), s->length());
                s = & values.getAt(index++);
            }
        } else {
            _visitor->VisitKeyword(&item, s->c_str(), s->length());
        }
    }
}

class JuniperQueryAdapter : public juniper::IQuery
{
private:
    KeywordExtractor *_kwExtractor;
    const vespalib::stringref _buf;
    const search::fef::Properties *_highlightTerms;

public:
    JuniperQueryAdapter(const JuniperQueryAdapter&) = delete;
    JuniperQueryAdapter operator= (const JuniperQueryAdapter&) = delete;
    JuniperQueryAdapter(KeywordExtractor *kwExtractor, vespalib::stringref buf,
                        const search::fef::Properties *highlightTerms = nullptr)
        : _kwExtractor(kwExtractor), _buf(buf), _highlightTerms(highlightTerms) {}

    // TODO: put this functionality into the stack dump iterator
    bool SkipItem(search::SimpleQueryStackDumpIterator *iterator) const
    {
        uint32_t skipCount = iterator->getArity();

        while (skipCount > 0) {
            if (!iterator->next())
                return false; // stack too small
            skipCount = skipCount - 1 + iterator->getArity();
        }
        return true;
    }

    bool Traverse(juniper::IQueryVisitor *v) const override;

    bool UsefulIndex(const juniper::QueryItem* item) const override
    {
        if (_kwExtractor == nullptr) {
            return true;
        }
        auto index = item->get_index();
        return _kwExtractor->IsLegalIndex(index);
    }
};

bool
JuniperQueryAdapter::Traverse(juniper::IQueryVisitor *v) const
{
    bool rc = true;
    search::SimpleQueryStackDumpIterator iterator(_buf);
    JuniperDFWQueryItem item(&iterator);

    if (_highlightTerms->numKeys() > 0) {
        v->VisitAND(&item, 2);
    }
    while (rc && iterator.next()) {
        bool isSpecialToken = iterator.hasSpecialTokenFlag();
        switch (iterator.getType()) {
        case search::ParseItem::ITEM_OR:
        case search::ParseItem::ITEM_WEAK_AND:
        case search::ParseItem::ITEM_EQUIV:
        case search::ParseItem::ITEM_WORD_ALTERNATIVES:
            if (!v->VisitOR(&item, iterator.getArity()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_AND:
            if (!v->VisitAND(&item, iterator.getArity()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_NOT:
            if (!v->VisitANDNOT(&item, iterator.getArity()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_RANK:
            if (!v->VisitRANK(&item, iterator.getArity()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_TERM:
        case search::ParseItem::ITEM_EXACTSTRINGTERM:
        case search::ParseItem::ITEM_PURE_WEIGHTED_STRING:
            {
                vespalib::stringref term = iterator.getTerm();
                v->VisitKeyword(&item, term.data(), term.size(), false, isSpecialToken);
            }
            break;
        case search::ParseItem::ITEM_NUMTERM:
            {
                vespalib::string term = iterator.getTerm();
                queryeval::SplitFloat splitter(term);
                if (splitter.parts() > 1) {
                    if (v->VisitPHRASE(&item, splitter.parts())) {
                        for (size_t i = 0; i < splitter.parts(); ++i) {
                            v->VisitKeyword(&item,
                                    splitter.getPart(i).c_str(),
                                    splitter.getPart(i).size(), false);
                        }
                    }
                } else if (splitter.parts() == 1) {
                    v->VisitKeyword(&item,
                                    splitter.getPart(0).c_str(),
                                    splitter.getPart(0).size(), false);
                } else {
                    v->VisitKeyword(&item, term.c_str(), term.size(), false, true);
                }
            }
            break;
        case search::ParseItem::ITEM_PHRASE:
            if (!v->VisitPHRASE(&item, iterator.getArity()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_PREFIXTERM:
        case search::ParseItem::ITEM_SUBSTRINGTERM:
            {
                vespalib::stringref term = iterator.getTerm();
                v->VisitKeyword(&item, term.data(), term.size(), true, isSpecialToken);
            }
            break;
        case search::ParseItem::ITEM_ANY:
            if (!v->VisitANY(&item, iterator.getArity()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_NEAR:
            if (!v->VisitNEAR(&item, iterator.getArity(),iterator.getNearDistance()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_ONEAR:
            if (!v->VisitWITHIN(&item, iterator.getArity(),iterator.getNearDistance()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_TRUE:
        case search::ParseItem::ITEM_FALSE:
            break;
        // Unhandled items are just ignored by juniper
        case search::ParseItem::ITEM_WAND:
        case search::ParseItem::ITEM_WEIGHTED_SET:
        case search::ParseItem::ITEM_DOT_PRODUCT:
        case search::ParseItem::ITEM_PURE_WEIGHTED_LONG:
        case search::ParseItem::ITEM_SUFFIXTERM:
        case search::ParseItem::ITEM_REGEXP:
        case search::ParseItem::ITEM_PREDICATE_QUERY:
        case search::ParseItem::ITEM_SAME_ELEMENT:
        case search::ParseItem::ITEM_NEAREST_NEIGHBOR:
        case search::ParseItem::ITEM_GEO_LOCATION_TERM:
            if (!v->VisitOther(&item, iterator.getArity())) {
                rc = SkipItem(&iterator);
            }
            break;
        default:
            rc = false;
        }
    }

    if (_highlightTerms->numKeys() > 1) {
        v->VisitAND(&item, _highlightTerms->numKeys());
    }
    TermVisitor tv(v);
    _highlightTerms->visitProperties(tv);

    return rc;
}

JuniperDFW::JuniperDFW(const juniper::Juniper * juniper)
    : _input_field_name(),
      _juniperConfig(),
      _juniper(juniper)
{
}


JuniperDFW::~JuniperDFW() = default;

bool
JuniperDFW::Init(
        const char *fieldName,
        const vespalib::string& inputField)
{
    bool rc = true;
    _juniperConfig = _juniper->CreateConfig(fieldName);
    if ( ! _juniperConfig) {
        LOG(warning, "could not create juniper config for field '%s'", fieldName);
        rc = false;
    }

    _input_field_name = inputField;
    return rc;
}

bool
JuniperTeaserDFW::Init(
        const char *fieldName,
        const vespalib::string& inputField)
{
    return JuniperDFW::Init(fieldName, inputField);
}

vespalib::string
DynamicTeaserDFW::makeDynamicTeaser(uint32_t docid, vespalib::stringref input, GetDocsumsState *state) const
{
    if (!state->_dynteaser._query) {
        JuniperQueryAdapter iq(state->_kwExtractor,
                               state->_args.getStackDump(),
                               &state->_args.highlightTerms());
        state->_dynteaser._query = _juniper->CreateQueryHandle(iq, nullptr);
    }

    LOG(debug, "makeDynamicTeaser: docid (%d)",
        docid);

    std::unique_ptr<juniper::Result> result;

    if (state->_dynteaser._query != nullptr) {

        if (LOG_WOULD_LOG(spam)) {
            std::ostringstream hexDump;
            hexDump << vespalib::HexDump(input.data(), input.length());
            LOG(spam, "makeDynamicTeaser: docid=%d, input='%s', hexdump:\n%s",
                docid, std::string(input.data(), input.length()).c_str(), hexDump.str().c_str());
        }

        auto langid = static_cast<uint32_t>(-1);

        result = juniper::Analyse(*_juniperConfig, *state->_dynteaser._query,
                                  input.data(), input.length(), docid, langid);
    }

    juniper::Summary *teaser = result
                               ? juniper::GetTeaser(*result, _juniperConfig.get())
                               : nullptr;

    if (LOG_WOULD_LOG(debug)) {
        std::ostringstream hexDump;
        if (teaser != nullptr) {
            hexDump << vespalib::HexDump(teaser->Text(), teaser->Length());
        }
        LOG(debug, "makeDynamicTeaser: docid=%d, teaser='%s', hexdump:\n%s",
            docid, (teaser != nullptr ? std::string(teaser->Text(), teaser->Length()).c_str() : "nullptr"),
            hexDump.str().c_str());
    }

    if (teaser != nullptr) {
        return {teaser->Text(), teaser->Length()};
    } else {
        return {};
    }
}

void
DynamicTeaserDFW::insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState *state, ResType,
                              vespalib::slime::Inserter &target) const
{
    if (doc != nullptr) {
        auto input = doc->get_juniper_input(_input_field_name);
        if (!input.empty()) {
            vespalib::string teaser = makeDynamicTeaser(docid, input.get_value(), state);
            vespalib::Memory value(teaser.c_str(), teaser.size());
            target.insertString(value);
        }
    }
}

}
