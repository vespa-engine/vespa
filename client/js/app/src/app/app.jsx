import React from 'react';
import { BrowserRouter, Route } from 'react-router-dom';
import { Error, Layout } from 'app/components';
import { Home } from 'app/pages/home/home';
import { QueryBuilder } from 'app/pages/querybuilder/query-builder';
import { QueryTracer } from 'app/pages/querytracer/query-tracer';
import { AppProvider } from 'app/libs/app-provider';
import { AppRouter } from 'app/libs/app-router';

export function App() {
  return (
    <BrowserRouter>
      <AppProvider>
        <Layout>
          <AppRouter>
            <Route path="/" element={<Home />} />
            <Route path="querybuilder" element={<QueryBuilder />} />
            <Route path="querytracer" element={<QueryTracer />} />
            <Route path="*" element={<Error code={404} />} />
          </AppRouter>
        </Layout>
      </AppProvider>
    </BrowserRouter>
  );
}
