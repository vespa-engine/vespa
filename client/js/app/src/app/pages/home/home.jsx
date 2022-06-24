import React from 'react';
import { Container, SimpleGrid, Space } from '@mantine/core';
import { Link, CardLink, Icon } from 'app/components';

// TODO: move SimpleGrid to components

export function Home() {
  return (
    <Container>
      <Space h={55} />
      <SimpleGrid
        style={{ gridAutoRows: 'minmax(0, 144px)' }}
        breakpoints={[
          { maxWidth: 'sm', cols: 2, spacing: 'sm' },
          { maxWidth: 'xs', cols: 1, spacing: 'sm' },
        ]}
        spacing="lg"
        cols={2}
      >
        <CardLink component={Link} to="/querybuilder">
          <Icon name="arrows-to-dot" size="2x" />
          query builder
        </CardLink>
        <CardLink component={Link} to="/querytracer">
          <Icon name="chart-gantt" size="2x" />
          query tracer
        </CardLink>
      </SimpleGrid>
    </Container>
  );
}
