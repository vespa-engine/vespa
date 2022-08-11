import React from 'react';
import { useQueryBuilderContext } from 'app/pages/querybuilder/context/query-builder-provider';

export function QueryDerived() {
  const query = useQueryBuilderContext((ctx) => ctx.query.input);
  return <textarea readOnly cols="70" rows="15" value={query}></textarea>;
}
