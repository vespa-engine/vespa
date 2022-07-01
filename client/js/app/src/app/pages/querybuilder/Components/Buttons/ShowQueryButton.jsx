import React, { useContext, useState } from 'react';
import { QueryContext } from '../Contexts/QueryContext';
import SimpleButton from './SimpleButton';

export default function ShowQueryButton() {
  const { query, showQuery, setShowQuery } = useContext(QueryContext);
  //console.log(showQuery);

  const handleClick = () => {
    setShowQuery(!showQuery);
  };

  return (
    <>
      <SimpleButton className="showJSON" onClick={handleClick}>
        Show query JSON
      </SimpleButton>
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
