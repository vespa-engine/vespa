import React from 'react';
import QueryInput from 'app/pages/querybuilder/query-filters/QueryInput';
import PasteJSONButton from 'app/pages/querybuilder/query-filters/PasteJSONButton';
import { Container } from 'app/components';

export function QueryFilters() {
  return (
    <Container sx={{ alignContent: 'start' }}>
      <QueryInput />
      <PasteJSONButton />
    </Container>
  );
}
