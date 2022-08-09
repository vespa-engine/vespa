import React from 'react';
import { useQueryBuilderContext } from 'app/pages/querybuilder/Components/Contexts/QueryBuilderProvider';

export default function ResponseBox() {
  const response = useQueryBuilderContext((ctx) => ctx.http.response);

  return (
    <textarea
      id="responsetext"
      className="responsebox"
      readOnly
      cols="70"
      rows="25"
      value={response}
    />
  );
}
