// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.common.cachedselect");
#include "cachedselect.h"
#include <vespa/document/select/valuenode.h>
#include <vespa/document/select/cloningvisitor.h>
#include "attributefieldvaluenode.h"
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/document/select/parser.h>
#include "selectpruner.h"

namespace proton
{

using search::index::Schema;
using search::AttributeVector;
using search::AttributeGuard;
using document::select::FieldValueNode;
using search::attribute::CollectionType;

class AttrVisitor : public document::select::CloningVisitor
{
public:
    typedef std::map<vespalib::string, uint32_t> AttrMap;

    AttrMap _amap;
    const search::IAttributeManager &_amgr;
    CachedSelect &_cached;
    const Schema &_schema;
    uint32_t _svAttrs;
    uint32_t _mvAttrs;
    uint32_t _complexAttrs;

    uint32_t
    getFieldNodes(void) const
    {
        return _fieldNodes;
    }

    static uint32_t
    invalidIdx(void)
    {
        return std::numeric_limits<uint32_t>::max();
    }
    
    AttrVisitor(const Schema &schema,
                const search::IAttributeManager &amgr,
                CachedSelect &cachedSelect);

    /*
     * Mutate field value nodes representing single value attributes into
     * attribute field valulue nodes.
     */
    virtual void
    visitFieldValueNode(const FieldValueNode &expr) override;
};


AttrVisitor::AttrVisitor(const Schema &schema,
                         const search::IAttributeManager &amgr,
                         CachedSelect &cachedSelect)
    : CloningVisitor(),
      _amap(),
      _amgr(amgr),
      _cached(cachedSelect),
      _schema(schema),
      _svAttrs(0u),
      _mvAttrs(0u),
      _complexAttrs(0u)
{
}


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
    if (_schema.isAttributeField(name)) {
        if (complex) {
            ++_complexAttrs;
            // Don't try to optimize complex attribute references yet.
            _valueNode = expr.clone();
            return;
        }
        AttributeGuard::UP ag(_amgr.getAttribute(name));
        assert(ag.get() != NULL);
        if (!ag->valid()) {
            // Fast access document sub where attribute is not marked as
            // fast-access.  Disable optimization.
            ++_complexAttrs;
            _valueNode = expr.clone();
            return;
        }
        AttributeVector::SP av(ag->getSP());
        if (av->getCollectionType() == CollectionType::SINGLE) {
            ++_svAttrs;
            AttrMap::iterator it(_amap.find(name));
            uint32_t idx(invalidIdx());
            if (it == _amap.end()) {
                // Allocate new location for guard
                idx = _cached._attributes.size();
                _amap[name] = idx;
                _cached._attributes.push_back(av);
            } else {
                // Already allocated location for guard
                idx = it->second;
            }
            assert(idx != invalidIdx());
            _valueNode.reset(new AttributeFieldValueNode(expr.getDocType(),
                                                         name,
                                                         av));
        } else {
            // Don't try to optimize multivalue attribute vectors yet
            ++_mvAttrs;
            _valueNode = expr.clone();
        }
    } else {
        _valueNode = expr.clone();
    }
}


CachedSelect::CachedSelect(void)
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
        document::select::Parser parser(repo,
                                        document::BucketIdFactory());
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
                  const search::index::Schema &schema,
                  const search::IAttributeManager *amgr,
                  bool hasFields)
{                  
    typedef std::unique_ptr<document::select::Node> NodeUP;

    set(selection, repo);
    NodeUP parsed(std::move(_select));
    if (parsed.get() == NULL)
        return;
    SelectPruner pruner(docTypeName,
                        schema,
                        emptyDoc,
                        repo,
                        hasFields);
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
    AttrVisitor av(schema, *amgr, *this);
    _select->visit(av);
    assert(_fieldNodes == av.getFieldNodes());
    assert(_attrFieldNodes == av._mvAttrs + av._svAttrs + av._complexAttrs);
    _svAttrFieldNodes = av._svAttrs;
    if (_fieldNodes == _svAttrFieldNodes) {
        _attrSelect = std::move(av.getNode());
    }
}


} // namespace proton

