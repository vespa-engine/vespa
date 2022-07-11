import React, { createContext, useState } from 'react';

export const ResponseContext = createContext();

export const ResponseProvider = (prop) => {
  const [response, setResponse] = useState('');

  return (
    <ResponseContext.Provider value={{ response, setResponse }}>
      {prop.children}
    </ResponseContext.Provider>
  );
};
