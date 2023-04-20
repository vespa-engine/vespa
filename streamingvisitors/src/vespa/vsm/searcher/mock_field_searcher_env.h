// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldsearcher.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/tablemanager.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/searchvisitor/indexenvironment.h>
#include <vespa/searchvisitor/queryenvironment.h>
#include <vespa/vsm/common/storagedocument.h>

namespace vsm::test {

/**
 * Mock of the objects needed to prepare a FieldSearcher.
 * Only used for unit testing.
 */
struct MockFieldSearcherEnv {
    SharedSearcherBuf buf;
    vsm::SharedFieldPathMap field_paths;
    search::fef::TableManager table_mgr;
    streaming::IndexEnvironment index_env;
    search::attribute::test::MockAttributeManager attr_mgr;
    search::fef::Properties query_props;
    streaming::QueryEnvironment query_env;
    MockFieldSearcherEnv()
        : buf(new SearcherBuf(8)),
          field_paths(std::make_shared<FieldPathMapT>()),
          table_mgr(),
          index_env(table_mgr),
          attr_mgr(),
          query_props(),
          query_env("", index_env, query_props, &attr_mgr)
    {}
    ~MockFieldSearcherEnv() {}
    void prepare(FieldSearcher& searcher, search::streaming::QueryTermList& qtl) {
        searcher.prepare(qtl, buf, *field_paths, query_env);
    }
    void prepare(FieldIdTSearcherMap& searcher_map,
                 const DocumentTypeIndexFieldMapT& difm,
                 search::streaming::Query& query) {
        searcher_map.prepare(difm, buf, query, *field_paths, query_env);
    }
};

}

