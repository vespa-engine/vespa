import React, { createContext, useState } from 'react';
import { levelZeroParameters } from 'app/pages/querybuilder/parameters';

export const QueryInputContext = createContext();

export const QueryInputProvider = (prop) => {
  // This is the id of the newest QueryInput, gets updated each time a new one is added
  const [id, setId] = useState(1);

  const firstChoice = levelZeroParameters[Object.keys(levelZeroParameters)[0]];

  const [inputs, setInputs] = useState([
    {
      id: '1',
      type: firstChoice.name,
      typeof: firstChoice.type,
      input: '',
      hasChildren: false,
      children: [],
    },
  ]);

  const [selectedItems, setSelectedItems] = useState([]);

  return (
    <QueryInputContext.Provider
      value={{
        inputs,
        setInputs,
        id,
        setId,
        selectedItems,
        setSelectedItems,
      }}
    >
      {prop.children}
    </QueryInputContext.Provider>
  );
};
