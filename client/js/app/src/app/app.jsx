import React from 'react';
import { Layout } from 'app/components';
import { AppRouter } from 'app/libs/app-router';
import { Home } from 'app/pages/home/home';
import { QueryBuilder } from 'app/pages/querybuilder/query-builder';

export function App() {
  return (
    <Layout>
      <AppRouter>
        <Home path="/" />
        <QueryBuilder path="querybuilder" />
      </AppRouter>
    </Layout>
  );
}
