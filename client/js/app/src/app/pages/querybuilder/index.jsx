import React from 'react';
import { SimpleGrid, Title } from '@mantine/core';
import { Container, Content, Icon } from 'app/components';
import { QueryBuilderProvider } from 'app/pages/querybuilder/context/query-builder-provider';
import { QueryFilters } from 'app/pages/querybuilder/query-filters/query-filters';
import { QueryDerived } from 'app/pages/querybuilder/query-derived/query-derived';
import { QueryResponse } from 'app/pages/querybuilder/query-response/query-response';
import QueryEndpoint from 'app/pages/querybuilder/query-endpoint/query-endpoint';

export function QueryBuilder() {
  return (
    <QueryBuilderProvider>
      <Container sx={{ rowGap: '21px' }}>
        <Title order={2}>
          <Icon name="arrows-to-dot" /> Query Builder
        </Title>
        <QueryEndpoint />
        <SimpleGrid
          breakpoints={[{ maxWidth: 'sm', cols: 1 }]}
          cols={3}
          spacing="lg"
        >
          <Content>
            <QueryFilters />
          </Content>
          <Content>
            <QueryDerived />
          </Content>
          <Content>
            <QueryResponse />
          </Content>
        </SimpleGrid>
      </Container>
    </QueryBuilderProvider>
  );
}
