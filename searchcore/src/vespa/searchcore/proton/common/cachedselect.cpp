// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefieldvaluenode.h"
#include "cachedselect.h"
#include "selectcontext.h"
#include "selectpruner.h"
#include <vespa/document/select/parser.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/log/log.h>

LOG_SETUP(".proton.common.cachedselect");

namespace proton {

using search::AttributeVector;
using search::AttributeGuard;
using document::select::FieldValueNode;
using search::attribute::CollectionType;

namespace {

class AttrVisitor : public document::select::CloningVisitor
{
public:
    typedef std::map<vespalib::string, uint32_t> AttrMap;

    AttrMap _amap;
    const search::IAttributeManager &_amgr;
    CachedSelect::AttributeVectors &_attributes;
    uint32_t _svAttrs;
    uint32_t _mvAttrs;
    uint32_t _complexAttrs;

    uint32_t getFieldNodes() const { return _fieldNodes; }

    static uint32_t invalidIdx() {
        return std::numeric_limits<uint32_t>::max();
    }
    
    AttrVisitor(const search::IAttributeManager &amgr, CachedSelect::AttributeVectors &attributes);
    ~AttrVisitor();

    /*
     * Mutate field value nodes representing single value attributes into
     * attribute field valulue nodes.
     */
    void visitFieldValueNode(const FieldValueNode &expr) override;
};


AttrVisitor::AttrVisitor(const search::IAttributeManager &amgr, CachedSelect::AttributeVectors &attributes)
    : CloningVisitor(),
      _amap(),
      _amgr(amgr),
      _attributes(attributes),
      _svAttrs(0u),
      _mvAttrs(0u),
      _complexAttrs(0u)
{}

AttrVisitor::~AttrVisitor() { }

void
AttrVisitor::visitFieldValueNode(const FieldValueNode &expr)
{
    ++_fieldNodes;
    // Expression has survived select pruner, thus we know that field is
    // valid for document type.
    vespalib::string name(expr.getFieldName()); 
    bool complex = false;
    for (uint32_t i = 0; i < name.size(); ++i) {
        if (name[i] == '.' || name[i] == '{' || name[i] == '[') {
            // TODO: Check for struct, array, map or weigthed set
            name = expr.getFieldName().substr(0, i);
            complex = true;
            break;
        }
    }
    AttributeGuard::UP ag(_amgr.getAttribute(name));
    if (ag->valid()) {
        if (complex) {
            ++_complexAttrs;
            // Don't try to optimize complex attribute references yet.
            _valueNode = expr.clone();
            return;
        }
        std::shared_ptr<search::AttributeVector> av(ag->getSP());
        if (av->getCollectionType() == CollectionType::SINGLE) {
            ++_svAttrs;
            AttrMap::iterator it(_amap.find(name));
            uint32_t idx(invalidIdx());
            if (it == _amap.end()) {
                // Allocate new location for guard
                idx = _attributes.size();
                _amap[name] = idx;
                _attributes.push_back(av);
            } else {
                // Already allocated location for guard
                idx = it->second;
            }
            assert(idx != invalidIdx());
            _valueNode.reset(new AttributeFieldValueNode(expr.getDocType(), name, av));
        } else {
            // Don't try to optimize multivalue attribute vectors yet
            ++_mvAttrs;
            _valueNode = expr.clone();
        }
    } else {
        _valueNode = expr.clone();
    }
}

}

CachedSelect::Session::Session(std::unique_ptr<document::select::Node> select, bool isAttrSelect)
    : _select(std::move(select)),
      _isAttrSelect(isAttrSelect)
{
}

bool
CachedSelect::Session::contains(const SelectContext &context) const
{
    return (!_isAttrSelect) ||
            (_isAttrSelect && (_select->contains(context) == document::select::Result::True));
}

bool
CachedSelect::Session::contains(const document::Document &doc) const
{
    return (_isAttrSelect || (_select->contains(doc) == document::select::Result::True));
}

CachedSelect::CachedSelect()
    : _attributes(),
      _select(),
      _fieldNodes(0u),
      _attrFieldNodes(0u),
      _svAttrFieldNodes(0u),
      _resultSet(),
      _allFalse(false),
      _allTrue(false),
      _allInvalid(false),
      _attrSelect()
{ }

CachedSelect::~CachedSelect() { }

void
CachedSelect::set(const vespalib::string &selection,
                  const document::DocumentTypeRepo &repo)
{
    try {
        document::select::Parser parser(repo, document::BucketIdFactory());
        _select = parser.parse(selection);
    } catch (document::select::ParsingFailedException &) {
        _select.reset(NULL);
    }
    _allFalse = _select.get() == NULL;
    _allTrue = false;
    _allInvalid = false;
}

                  
void
CachedSelect::set(const vespalib::string &selection,
                  const vespalib::string &docTypeName,
                  const document::Document &emptyDoc,
                  const document::DocumentTypeRepo &repo,
                  const search::IAttributeManager *amgr,
                  bool hasFields)
{                  
    typedef std::unique_ptr<document::select::Node> NodeUP;

    set(selection, repo);
    NodeUP parsed(std::move(_select));
    if (parsed.get() == NULL)
        return;
    SelectPruner pruner(docTypeName,
                        amgr,
                        emptyDoc,
                        repo,
                        hasFields,
                        true);
    pruner.process(*parsed);
    _resultSet = pruner.getResultSet();
    _allFalse = pruner.isFalse();
    _allTrue = pruner.isTrue();
    _allInvalid = pruner.isInvalid();
    _select = std::move(pruner.getNode());
    _fieldNodes = pruner.getFieldNodes();
    _attrFieldNodes = pruner.getAttrFieldNodes();
    if (amgr == NULL || _attrFieldNodes == 0u)
        return;
    AttrVisitor av(*amgr, _attributes);
    _select->visit(av);
    assert(_fieldNodes == av.getFieldNodes());
    assert(_attrFieldNodes == av._mvAttrs + av._svAttrs + av._complexAttrs);
    _svAttrFieldNodes = av._svAttrs;
    if (_fieldNodes == _svAttrFieldNodes) {
        _attrSelect = std::move(av.getNode());
    }
}

std::unique_ptr<CachedSelect::Session>
CachedSelect::createSession() const
{
    return (_attrSelect ? std::make_unique<Session>(_attrSelect->clone(), true) :
            std::make_unique<Session>(_select->clone(), false));
}

}

