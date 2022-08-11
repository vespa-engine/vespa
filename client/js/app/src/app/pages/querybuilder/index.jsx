import React from 'react';
import { QueryBuilderProvider } from 'app/pages/querybuilder/context/query-builder-provider';
import { QueryFilters } from 'app/pages/querybuilder/query-filters/query-filters';
import { QueryDerived } from 'app/pages/querybuilder/query-derived/query-derived';
import { QueryResponse } from 'app/pages/querybuilder/query-response/query-response';

export function QueryBuilder() {
  return (
    <QueryBuilderProvider>
      <QueryFilters />
      <QueryDerived />
      <QueryResponse />
    </QueryBuilderProvider>
  );
}
