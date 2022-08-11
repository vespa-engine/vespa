import React from 'react';
import SendQuery from 'app/pages/querybuilder/query-filters/SendQuery';
import QueryInput from 'app/pages/querybuilder/query-filters/QueryInput';
import PasteJSONButton from 'app/pages/querybuilder/query-filters/PasteJSONButton';

export function QueryFilters() {
  return (
    <>
      <SendQuery />
      <QueryInput />
      <PasteJSONButton />
    </>
  );
}
