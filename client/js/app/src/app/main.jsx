import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from 'app/app';
import { ResponseProvider } from './pages/querybuilder/Components/Contexts/ResponseContext';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ResponseProvider>
      <App />
    </ResponseProvider>
  </React.StrictMode>
);
