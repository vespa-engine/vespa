// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniperdfw.h"
#include "docsumwriter.h"
#include "docsumstate.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/queryeval/split_float.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <vespa/juniper/config.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.dynamicteaserdfw");

namespace juniper {

struct ExplicitItemData
{
    const char *_index;
    uint32_t _indexlen;
    const char *_term;
    uint32_t _termlen;
    uint32_t _weight;

    ExplicitItemData()
        : _index(NULL), _indexlen(0), _term(NULL), _termlen(0), _weight(0)
        {}

    ExplicitItemData(const char *index, uint32_t indexlen, const char* term,
                     uint32_t termlen, uint32_t weight = 0)
        : _index(index), _indexlen(indexlen), _term(term), _termlen(termlen), _weight(weight)
        {}
};



/**
 * This struct is used to point to the traversal state located on
 * the stack of the IQuery Traverse method. This is needed because
 * the Traverse method is const.
 **/
struct QueryItem
{
    search::SimpleQueryStackDumpIterator *_si;
    const ExplicitItemData *_data;
    QueryItem() : _si(NULL), _data(NULL) {}
    QueryItem(search::SimpleQueryStackDumpIterator *si) : _si(si), _data(NULL) {}
    QueryItem(ExplicitItemData *data) : _si(NULL), _data(data) {}
private:
    QueryItem(const QueryItem&);
    QueryItem& operator= (const QueryItem&);
};
};

namespace search {
class Property;

namespace fef {
class TermVisitor : public IPropertiesVisitor
{
public:
    juniper::IQueryVisitor *_visitor;
    juniper::QueryItem _item;

    TermVisitor(juniper::IQueryVisitor *visitor) :
        _visitor(visitor), _item() {}

    virtual void visitProperty(const Property::Value &key, const Property &values) override;

};

void
TermVisitor::visitProperty(const Property::Value &key, const Property &values)
{
    juniper::ExplicitItemData data;
    juniper::QueryItem item(&data);
    int index = 0;
    int numBlocks = atoi(values.getAt(index++).c_str());
    data._index = key.c_str();
    data._indexlen = key.length();

    _visitor->VisitAND(&item, numBlocks);

    for (int i = 0; i < numBlocks; i++) {
        const Property::Value * s = & values.getAt(index++);
        if ((*s)[0] == '"') {
            s = & values.getAt(index++);
            int phraseLen = atoi(s->c_str());
            _visitor->VisitPHRASE(&item, phraseLen);
            s = & values.getAt(index++);
            while ((*s)[0] != '"') {
                data._term = s->c_str();
                data._termlen = s->length();
                _visitor->VisitKeyword(&item, s->c_str(), s->length());
                s = & values.getAt(index++);
            }
        } else {
            data._term = s->c_str();
            data._termlen = s->length();
            _visitor->VisitKeyword(&item, s->c_str(), s->length());
        }
    }
}

}

namespace docsummary {

class JuniperQueryAdapter : public juniper::IQuery
{
private:
    JuniperQueryAdapter(const JuniperQueryAdapter&);
    JuniperQueryAdapter operator= (const JuniperQueryAdapter&);

    KeywordExtractor *_kwExtractor;
    const vespalib::stringref _buf;
    const search::fef::Properties *_highlightTerms;
    juniper::IQueryVisitor *_visitor;

public:
    JuniperQueryAdapter(KeywordExtractor *kwExtractor,
                        const vespalib::stringref &buf,
                        const search::fef::Properties *highlightTerms = NULL)
        : _kwExtractor(kwExtractor), _buf(buf), _highlightTerms(highlightTerms), _visitor(NULL) {}

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

    virtual bool Traverse(juniper::IQueryVisitor *v) const override;

    virtual int Weight(const juniper::QueryItem* item) const override
    {
        if (item->_si != NULL) {
            return item->_si->GetWeight().percent();
        } else {
            return item->_data->_weight;
        }
    }
    virtual juniper::ItemCreator Creator(const juniper::QueryItem* item) const override
    {
        // cast master: Knut Omang
        if (item->_si != NULL) {
            return (juniper::ItemCreator) item->_si->getCreator();
        } else {
            return juniper::CREA_ORIG;
        }
    }
    virtual const char *Index(const juniper::QueryItem* item, size_t *len) const override
    {
        if (item->_si != NULL) {
            *len = item->_si->getIndexName().size();
            return item->_si->getIndexName().c_str();
        } else {
            *len = item->_data->_indexlen;
            return item->_data->_index;
        }

    }
    virtual bool UsefulIndex(const juniper::QueryItem* item) const override
    {
        vespalib::stringref index;

        if (_kwExtractor == NULL)
            return true;

        if (item->_si != NULL) {
            index = item->_si->getIndexName();
        } else {
            index = vespalib::stringref(item->_data->_index, item->_data->_indexlen);
        }
        return _kwExtractor->IsLegalIndex(index);
    }
};



bool
JuniperQueryAdapter::Traverse(juniper::IQueryVisitor *v) const
{
    bool rc = true;
    search::SimpleQueryStackDumpIterator iterator(_buf);
    juniper::QueryItem item(&iterator);

    if (_highlightTerms->numKeys() > 0) {
        v->VisitAND(&item, 2);
    }
    while (rc && iterator.next()) {
        bool isSpecialToken = search::ParseItem::getFlag(iterator.getFlags(), search::ParseItem::IFLAG_SPECIALTOKEN);
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
                v->VisitKeyword(&item, term.c_str(), term.size(), false, isSpecialToken);
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
        case search::ParseItem::ITEM_PAREN:
            if (!v->VisitOther(&item, iterator.getArity()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_PREFIXTERM:
        case search::ParseItem::ITEM_SUBSTRINGTERM:
            {
                vespalib::stringref term = iterator.getTerm();
                v->VisitKeyword(&item, term.c_str(), term.size(), true, isSpecialToken);
            }
            break;
        case search::ParseItem::ITEM_ANY:
            if (!v->VisitANY(&item, iterator.getArity()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_NEAR:
            if (!v->VisitNEAR(&item, iterator.getArity(),iterator.getArg1()))
                rc = SkipItem(&iterator);
            break;
        case search::ParseItem::ITEM_ONEAR:
            if (!v->VisitWITHIN(&item, iterator.getArity(),iterator.getArg1()))
                rc = SkipItem(&iterator);
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
    fef::TermVisitor tv(v);
    _highlightTerms->visitProperties(tv);

    return rc;
}

JuniperDFW::JuniperDFW(juniper::Juniper * juniper)
    : _inputFieldEnumValue(static_cast<uint32_t>(-1))
    , _juniperConfig()
    , _langFieldEnumValue(static_cast<uint32_t>(-1))
    , _juniper(juniper)
{
}


JuniperDFW::~JuniperDFW() { }

bool
JuniperDFW::Init(
        const char *fieldName,
        const char *langFieldName,
        const ResultConfig & config,
        const char *inputField)
{
    bool rc = true;
    const util::StringEnum & enums(config.GetFieldNameEnum());
    if (langFieldName != NULL)
        _langFieldEnumValue = enums.Lookup(langFieldName);
    _juniperConfig = _juniper->CreateConfig(fieldName);
    if (_juniperConfig.get() == NULL) {
        LOG(warning, "could not create juniper config for field '%s'", fieldName);
        rc = false;
    }

    _inputFieldEnumValue = enums.Lookup(inputField);

    if (_inputFieldEnumValue >= enums.GetNumEntries()) {
        LOG(warning, "no docsum format contains field '%s'; dynamic teasers will be empty",
            inputField);
    }
    return rc;
}

bool
JuniperTeaserDFW::Init(
        const char *fieldName,
        const char *langFieldName,
        const ResultConfig & config,
        const char *inputField)
{
    bool rc = JuniperDFW::Init(fieldName, langFieldName, config, inputField);

    for (ResultConfig::const_iterator it(config.begin()), mt(config.end()); rc && it != mt; it++) {

        const ResConfigEntry *entry =
            it->GetEntry(it->GetIndexFromEnumValue(_inputFieldEnumValue));

        if (entry != NULL &&
            !IsRuntimeCompatible(entry->_type, RES_STRING) &&
            !IsRuntimeCompatible(entry->_type, RES_DATA))
        {
            LOG(warning, "cannot use docsum field '%s' as input to dynamicteaser; bad type in result class %d (%s)",
                inputField, it->GetClassID(), it->GetClassName());
            rc = false;
        }
    }
    return rc;
}

vespalib::string
DynamicTeaserDFW::makeDynamicTeaser(uint32_t docid,
                                    GeneralResult *gres,
                                    GetDocsumsState *state)
{
    if (state->_dynteaser._query == NULL) {
        JuniperQueryAdapter iq(state->_kwExtractor,
                               state->_args.getStackDump(),
                               &state->_args.highlightTerms());
        state->_dynteaser._query = _juniper->CreateQueryHandle(iq, NULL);
    }

    if (docid != state->_dynteaser._docid ||
        _inputFieldEnumValue != state->_dynteaser._input ||
        _langFieldEnumValue != state->_dynteaser._lang ||
        !juniper::AnalyseCompatible(_juniperConfig.get(), state->_dynteaser._config)) {
        LOG(debug, "makeDynamicTeaser: docid (%d,%d), fieldenum (%d,%d), lang (%d,%d) analyse %s",
                docid, state->_dynteaser._docid,
                _inputFieldEnumValue, state->_dynteaser._input,
                _langFieldEnumValue, state->_dynteaser._lang,
                (juniper::AnalyseCompatible(_juniperConfig.get(), state->_dynteaser._config) ? "no" : "yes"));

        if (state->_dynteaser._result != NULL)
            juniper::ReleaseResult(state->_dynteaser._result);

        state->_dynteaser._docid  = docid;
        state->_dynteaser._input  = _inputFieldEnumValue;
        state->_dynteaser._lang   = _langFieldEnumValue;
        state->_dynteaser._config = _juniperConfig.get();
        state->_dynteaser._result = NULL;

        int idx = gres->GetClass()->GetIndexFromEnumValue(_inputFieldEnumValue);
        ResEntry *entry = gres->GetEntry(idx);

        if (entry != NULL &&
            state->_dynteaser._query != NULL) {

            // obtain Juniper input
            const char *buf;
            uint32_t    buflen;

            entry->_resolve_field(&buf, &buflen,
                                  &state->_docSumFieldSpace);

            if (LOG_WOULD_LOG(spam)) {
                std::ostringstream hexDump;
                hexDump << vespalib::HexDump(buf, buflen);
                LOG(spam, "makeDynamicTeaser: docid=%d, input='%s', hexdump:\n%s",
                        docid, std::string(buf, buflen).c_str(),
                        hexDump.str().c_str());
            }

            uint32_t langid = static_cast<uint32_t>(-1);

            state->_dynteaser._result =
                juniper::Analyse(_juniperConfig.get(), state->_dynteaser._query,
                                 buf, buflen, docid, _inputFieldEnumValue,  langid);
        }
    }

    juniper::Summary *teaser = (state->_dynteaser._result != NULL)
                               ? juniper::GetTeaser(state->_dynteaser._result, _juniperConfig.get())
                               : NULL;

    if (LOG_WOULD_LOG(debug)) {
        std::ostringstream hexDump;
        if (teaser != NULL) {
            hexDump << vespalib::HexDump(teaser->Text(), teaser->Length());
        }
        LOG(debug, "makeDynamicTeaser: docid=%d, teaser='%s', hexdump:\n%s",
            docid, (teaser != NULL ? std::string(teaser->Text(), teaser->Length()).c_str() : "NULL"),
            hexDump.str().c_str());
    }

    if (teaser != NULL) {
        return vespalib::string(teaser->Text(),
                                teaser->Length());
    } else {
        return vespalib::string();
    }
}

void
DynamicTeaserDFW::insertField(uint32_t docid,
                              GeneralResult *gres,
                              GetDocsumsState *state,
                              ResType,
                              vespalib::slime::Inserter &target)
{
    vespalib::string teaser = makeDynamicTeaser(docid, gres, state);
    vespalib::Memory value(teaser.c_str(), teaser.size());
    target.insertString(value);
}

} // namespace docsummary
} // namespace search

