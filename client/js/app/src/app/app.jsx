import React from 'react';
import { Layout } from 'app/components';
import { AppRouter } from 'app/libs/app-router';
import { Home } from 'app/pages/home/home';
import { QueryBuilder } from 'app/pages/querybuilder/query-builder';
import { AppProvider } from 'app/libs/app-provider';

export function App() {
  return (
    <AppProvider>
      <Layout>
        <AppRouter>
          <Home path="/" />
          <QueryBuilder path="querybuilder" />
        </AppRouter>
      </Layout>
    </AppProvider>
  );
}
