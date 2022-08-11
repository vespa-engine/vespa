import React from 'react';
import { SimpleGrid } from '@mantine/core';
import { Container } from 'app/components';
import { QueryBuilderProvider } from 'app/pages/querybuilder/context/query-builder-provider';
import { QueryFilters } from 'app/pages/querybuilder/query-filters/query-filters';
import { QueryDerived } from 'app/pages/querybuilder/query-derived/query-derived';
import { QueryResponse } from 'app/pages/querybuilder/query-response/query-response';
import QueryEndpoint from 'app/pages/querybuilder/query-endpoint/query-endpoint';

export function QueryBuilder() {
  return (
    <QueryBuilderProvider>
      <Container sx={{ rowGap: '21px' }}>
        <QueryEndpoint />
        <SimpleGrid cols={3}>
          <QueryFilters />
          <QueryDerived />
          <QueryResponse />
        </SimpleGrid>
      </Container>
    </QueryBuilderProvider>
  );
}
