import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from 'app/components';
import 'app/styles/index.css';
import { AppRouter } from 'app/libs/app-router';
import { QueryBuilder } from 'app/pages/querybuilder';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AppRouter>
      <App path="/" />
      <QueryBuilder path="querybuilder" />
    </AppRouter>
  </React.StrictMode>
);
