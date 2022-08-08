import React, { useContext } from 'react';
import { QueryContext } from '../Contexts/QueryContext';

export default function ShowQueryButton() {
  const { query, showQuery, setShowQuery } = useContext(QueryContext);

  const handleClick = () => {
    setShowQuery(!showQuery);
  };

  return (
    <>
      <button className="showJSON" onClick={handleClick}>
        Show query JSON
      </button>
      {showQuery && (
        <textarea
          id="jsonquery"
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
