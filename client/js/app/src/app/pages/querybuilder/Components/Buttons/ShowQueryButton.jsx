import React, { useState } from 'react';
import { useQueryBuilderContext } from 'app/pages/querybuilder/Components/Contexts/QueryBuilderProvider';

export default function ShowQueryButton() {
  const [showQuery, setShowQuery] = useState(false);
  const query = useQueryBuilderContext((ctx) => ctx.query.input);

  return (
    <>
      <button
        className="showJSON"
        onClick={() => setShowQuery((prev) => !prev)}
      >
        Show query JSON
      </button>
      {showQuery && (
        <textarea
          className="responsebox"
          readOnly
          cols="70"
          rows="15"
          value={query}
        ></textarea>
      )}
    </>
  );
}
