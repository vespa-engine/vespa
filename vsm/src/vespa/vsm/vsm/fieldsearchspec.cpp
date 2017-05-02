// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldsearchspec.h"
#include <vespa/vsm/searcher/utf8flexiblestringfieldsearcher.h>
#include <vespa/vsm/searcher/utf8strchrfieldsearcher.h>
#include <vespa/vsm/searcher/utf8substringsearcher.h>
#include <vespa/vsm/searcher/utf8suffixstringfieldsearcher.h>
#include <vespa/vsm/searcher/utf8exactstringfieldsearcher.h>
#include <vespa/vsm/searcher/futf8strchrfieldsearcher.h>
#include <vespa/vsm/searcher/intfieldsearcher.h>
#include <vespa/vsm/searcher/floatfieldsearcher.h>
#include <vespa/vespalib/util/regexp.h>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.fieldsearchspec");

#define DEBUGMASK 0x01

using search::Query;
using search::ConstQueryTermList;

namespace vsm {

FieldSearchSpec::FieldSearchSpec() :
    _id(0),
    _name(),
    _maxLength(0x100000),
    _searcher(),
    _searchMethod(VsmfieldsConfig::Fieldspec::NONE),
    _arg1(),
    _reconfigured(false)
{
}
FieldSearchSpec::~FieldSearchSpec() {}

FieldSearchSpec::FieldSearchSpec(const FieldIdT & fid, const vespalib::string & fname,
                                 VsmfieldsConfig::Fieldspec::Searchmethod searchDef, const vespalib::string & arg1, size_t maxLength_) :
    _id(fid),
    _name(fname),
    _maxLength(maxLength_),
    _searcher(),
    _searchMethod(searchDef),
    _arg1(arg1),
    _reconfigured(false)
{
    switch(searchDef) {
    default:
        LOG(warning, "Unknown searchdef = %d. Defaulting to AUTOUTF8", searchDef);
    case VsmfieldsConfig::Fieldspec::AUTOUTF8:
    case VsmfieldsConfig::Fieldspec::NONE:
    case VsmfieldsConfig::Fieldspec::SSE2UTF8:
    case VsmfieldsConfig::Fieldspec::UTF8:
        if (arg1 == "substring") {
            _searcher = UTF8SubStringFieldSearcher(fid);
        } else if (arg1 == "suffix") {
            _searcher = UTF8SuffixStringFieldSearcher(fid);
        } else if (arg1 == "exact") {
            _searcher = UTF8ExactStringFieldSearcher(fid);
        } else if (arg1 == "word") {
            _searcher = UTF8ExactStringFieldSearcher(fid);
        } else if (searchDef == VsmfieldsConfig::Fieldspec::UTF8) {
            _searcher = UTF8StrChrFieldSearcher(fid);
        } else {
            _searcher = FUTF8StrChrFieldSearcher(fid);
        }
        break;
    case VsmfieldsConfig::Fieldspec::INT8:
    case VsmfieldsConfig::Fieldspec::INT16:
    case VsmfieldsConfig::Fieldspec::INT32:
    case VsmfieldsConfig::Fieldspec::INT64:
        _searcher = IntFieldSearcher(fid);
        break;
    case VsmfieldsConfig::Fieldspec::FLOAT:
        _searcher = FloatFieldSearcher(fid);
        break;
    case VsmfieldsConfig::Fieldspec::DOUBLE:
        _searcher = DoubleFieldSearcher(fid);
        break;
    }
    if (_searcher.valid()) {
        if (arg1 == "prefix") {
            _searcher->setMatchType(FieldSearcher::PREFIX);
        } else if (arg1 == "substring") {
            _searcher->setMatchType(FieldSearcher::SUBSTRING);
        } else if (arg1 == "suffix") {
            _searcher->setMatchType(FieldSearcher::SUFFIX);
        } else if (arg1 == "exact") {
            _searcher->setMatchType(FieldSearcher::EXACT);
        } else if (arg1 == "word") {
            _searcher->setMatchType(FieldSearcher::EXACT);
        }
        _searcher->maxFieldLength(maxLength());
    }
}

void
FieldSearchSpec::reconfig(const search::QueryTerm & term)
{
    if (_reconfigured) {
        return;
    }
    switch (_searchMethod) {
    case VsmfieldsConfig::Fieldspec::NONE:
    case VsmfieldsConfig::Fieldspec::AUTOUTF8:
    case VsmfieldsConfig::Fieldspec::UTF8:
    case VsmfieldsConfig::Fieldspec::SSE2UTF8:
        if ((term.isSubstring() && _arg1 != "substring") ||
            (term.isSuffix() && _arg1 != "suffix") ||
            (term.isExactstring() && _arg1 != "exact") ||
            (term.isPrefix() && _arg1 == "suffix"))
        {
            _searcher = UTF8FlexibleStringFieldSearcher(id());
            // preserve the basic match property of the searcher
            if (_arg1 == "prefix") {
                _searcher->setMatchType(FieldSearcher::PREFIX);
            } else if (_arg1 == "substring") {
                _searcher->setMatchType(FieldSearcher::SUBSTRING);
            } else if (_arg1 == "suffix") {
                _searcher->setMatchType(FieldSearcher::SUFFIX);
            } else if (_arg1 == "exact") {
                _searcher->setMatchType(FieldSearcher::EXACT);
            } else if (_arg1 == "word") {
                _searcher->setMatchType(FieldSearcher::EXACT);
            }
            LOG(debug, "Reconfigured to use UTF8FlexibleStringFieldSearcher (%s) for field '%s' with id '%d'",
                _searcher->prefix() ? "prefix" : "regular", name().c_str(), id());
            _reconfigured = true;
        }
        break;
    default:
        break;
    }
}

vespalib::asciistream & operator <<(vespalib::asciistream & os, const FieldSearchSpec & f)
{
    os << f._id << ' ' << f._name << ' ';
    if (f._searcher.valid()) {
    } else {
        os << " No searcher defined.\n";
    }
    return os;
}

FieldSearchSpecMap::FieldSearchSpecMap() :
    _specMap(),
    _documentTypeMap(),
    _nameIdMap()
{ }

FieldSearchSpecMap::~FieldSearchSpecMap() {}

namespace {
    const vespalib::string _G_empty("");
    const vespalib::string _G_value(".value");
    const vespalib::Regexp _G_map1("\\{[a-zA-Z0-9]+\\}");
    const vespalib::Regexp _G_map2("\\{\".*\"\\}");
    const vespalib::Regexp _G_array("\\[[0-9]+\\]");
}

vespalib::string FieldSearchSpecMap::stripNonFields(const vespalib::string & rawIndex)
{
    if ((rawIndex.find('[') != vespalib::string::npos) || (rawIndex.find('{') != vespalib::string::npos)) {
        std::string index = _G_map1.replace(rawIndex, _G_value);
        index = _G_map2.replace(index, _G_value);
        index = _G_array.replace(index, _G_empty);
        return index;
    }
    return rawIndex;
}

bool FieldSearchSpecMap::buildFieldsInQuery(const Query & query, StringFieldIdTMap & fieldsInQuery) const
{
    bool retval(true);
    ConstQueryTermList qtl;
    query.getLeafs(qtl);

    for (ConstQueryTermList::const_iterator it = qtl.begin(), mt = qtl.end(); it != mt; it++) {
        for (DocumentTypeIndexFieldMapT::const_iterator dt(documentTypeMap().begin()), dmt(documentTypeMap().end()); dt != dmt; dt++) {
            const IndexFieldMapT & fim = dt->second;
            vespalib::string rawIndex((*it)->index());
            vespalib::string index(stripNonFields(rawIndex));
            IndexFieldMapT::const_iterator fIt = fim.find(index);
            if (fIt != fim.end()) {
                for(FieldIdTList::const_iterator ifIt = fIt->second.begin(), ifMt = fIt->second.end(); ifIt != ifMt; ifIt++) {
                    const FieldSearchSpec & spec = specMap().find(*ifIt)->second;
                    LOG(debug, "buildFieldsInQuery = rawIndex='%s', index='%s'", rawIndex.c_str(), index.c_str());
                    if ((rawIndex != index) && (spec.name().find(index) == 0)) {
                        vespalib::string modIndex(rawIndex);
                        modIndex.append(spec.name().substr(index.size()));
                        fieldsInQuery.add(modIndex, spec.id());
                    } else {
                        fieldsInQuery.add(spec.name(),spec.id());
                    }
                }
            } else {
                LOG(warning, "No valid indexes registered for index %s", (*it)->index().c_str());
                retval = false;
            }
        }
    }
    return retval;
}

void FieldSearchSpecMap::buildFieldsInQuery(const std::vector<vespalib::string> & otherFieldsNeeded, StringFieldIdTMap & fieldsInQuery) const
{
    for (size_t i(0), m(otherFieldsNeeded.size()); i < m; i++) {
        fieldsInQuery.add(otherFieldsNeeded[i], _nameIdMap.fieldNo(otherFieldsNeeded[i]));
    }
}


void FieldSearchSpecMap::buildFromConfig(const std::vector<vespalib::string> & otherFieldsNeeded)
{
    for(size_t i(0), m(otherFieldsNeeded.size()); i < m; i++) {
        LOG(debug, "otherFieldsNeeded[%zd] = '%s'", i, otherFieldsNeeded[i].c_str());
        _nameIdMap.add(otherFieldsNeeded[i]);
    }
}

bool FieldSearchSpecMap::buildFromConfig(const VsmfieldsHandle & conf)
{
    bool retval(true);
    LOG(spam, "Parsing %zd fields", conf->fieldspec.size());
    for(size_t i=0, m = conf->fieldspec.size(); i < m; i++) {
        const VsmfieldsConfig::Fieldspec & cfs = conf->fieldspec[i];
        LOG(spam, "Parsing %s", cfs.name.c_str());
        FieldIdT fieldId = specMap().size();
        FieldSearchSpec fss(fieldId, cfs.name, cfs.searchmethod, cfs.arg1.c_str(), cfs.maxlength);
        _specMap[fieldId] = fss;
        _nameIdMap.add(cfs.name, fieldId);
        LOG(spam, "M in %d = %s", fieldId, cfs.name.c_str());
    }

    LOG(spam, "Parsing %zd document types", conf->documenttype.size());
    for(size_t d=0, dm = conf->documenttype.size(); d < dm; d++) {
        const VsmfieldsConfig::Documenttype & di = conf->documenttype[d];
        IndexFieldMapT indexMapp;
        LOG(spam, "Parsing document type %s with %zd indexes", di.name.c_str(), di.index.size());
        for(size_t i=0, m = di.index.size(); i < m; i++) {
            const VsmfieldsConfig::Documenttype::Index & ci = di.index[i];
            LOG(spam, "Index %s with %zd fields", ci.name.c_str(), ci.field.size());
            FieldIdTList ifm;
            for (size_t j=0, n=ci.field.size(); j < n; j++) {
                const VsmfieldsConfig::Documenttype::Index::Field & cf = ci.field[j];
                LOG(spam, "Parsing field %s", cf.name.c_str());
                FieldSearchSpecMapT::const_iterator fIt, mIt;
                for (fIt=specMap().begin(), mIt=specMap().end(); (fIt != mIt) && (fIt->second.name() != cf.name); fIt++);
                if (fIt != mIt) {
                    ifm.push_back(fIt->second.id());
                } else {
                    LOG(warning, "Field %s not defined. Ignoring....", cf.name.c_str());
                }
            }
            indexMapp[ci.name] = ifm;
        }
        _documentTypeMap[di.name] = indexMapp;
    }
    return retval;
}

void
FieldSearchSpecMap::reconfigFromQuery(const search::Query & query)
{
    ConstQueryTermList qtl;
    query.getLeafs(qtl);

    for (ConstQueryTermList::const_iterator ita = qtl.begin(); ita != qtl.end(); ++ita) {
        for (DocumentTypeIndexFieldMapT::const_iterator itb = documentTypeMap().begin();
             itb != documentTypeMap().end(); ++itb)
        {
            const IndexFieldMapT & ifm = itb->second;
            IndexFieldMapT::const_iterator itc = ifm.find((*ita)->index());
            if (itc != ifm.end()) {
                for (FieldIdTList::const_iterator itd = itc->second.begin(); itd != itc->second.end(); ++itd) {
                    FieldSearchSpec & spec = _specMap.find(*itd)->second;
                    spec.reconfig(**ita);
                }
            }
        }
    }
}

bool lesserField(const FieldSearcherContainer & a, const FieldSearcherContainer & b)
{
    return a->field() < b->field();
}

void FieldSearchSpecMap::buildSearcherMap(const StringFieldIdTMapT & fieldsInQuery, FieldIdTSearcherMap & fieldSearcherMap)
{
    fieldSearcherMap.clear();
    for (StringFieldIdTMapT::const_iterator it = fieldsInQuery.begin(), mt = fieldsInQuery.end(); it != mt; it++) {
        FieldIdT fId = it->second;
        const FieldSearchSpec & spec = specMap().find(fId)->second;
        fieldSearcherMap.push_back(spec.searcher());
    }
    std::sort(fieldSearcherMap.begin(), fieldSearcherMap.end(), lesserField);
}


vespalib::asciistream & operator <<(vespalib::asciistream & os, const FieldSearchSpecMap & df)
{
    os << "DocumentTypeMap = \n";
    for (DocumentTypeIndexFieldMapT::const_iterator difIt=df.documentTypeMap().begin(), difMt=df.documentTypeMap().end(); difIt != difMt; difIt++) {
        os << "DocType = " << difIt->first << "\n";
        os << "IndexMap = \n";
        for (IndexFieldMapT::const_iterator ifIt=difIt->second.begin(), ifMt=difIt->second.end(); ifIt != ifMt; ifIt++) {
            os << ifIt->first << ": ";
            for (FieldIdTList::const_iterator it=ifIt->second.begin(), mt=ifIt->second.end(); it != mt; it++)
                os << *it << ' ';
            os << '\n';
        }
    }
    os << "SpecMap = \n";
    for (FieldSearchSpecMapT::const_iterator it=df.specMap().begin(), mt=df.specMap().end(); it != mt; it++)
        os << it->first << " = " << it->second << '\n';
    os << "NameIdMap = \n" << df.nameIdMap();
    return os;
}

}
