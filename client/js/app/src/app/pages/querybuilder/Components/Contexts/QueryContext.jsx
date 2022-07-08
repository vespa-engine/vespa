import React, { createContext, useState } from 'react';

export const QueryContext = createContext();

export const QueryProvider = (prop) => {
  const [query, setQuery] = useState('');
  const [showQuery, setShowQuery] = useState(false);

  return (
    <QueryContext.Provider value={{ query, setQuery, showQuery, setShowQuery }}>
      {prop.children}
    </QueryContext.Provider>
  );
};
