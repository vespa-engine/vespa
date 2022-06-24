import React from 'react';
import { BrowserRouter, Route } from 'react-router-dom';
import { Layout } from 'app/components';
import { Home } from 'app/pages/home/home';
import { QueryBuilder } from 'app/pages/querybuilder/query-builder';
import { QueryTracer } from 'app/pages/querytracer/query-tracer';
import { ThemeProvider } from 'app/libs/theme-provider';
import { Router } from 'app/libs/router';

export function App() {
  return (
    <BrowserRouter>
      <ThemeProvider>
        <Layout>
          <Router>
            <Route path="/" element={<Home />} />
            <Route path="querybuilder" element={<QueryBuilder />} />
            <Route path="querytracer" element={<QueryTracer />} />
          </Router>
        </Layout>
      </ThemeProvider>
    </BrowserRouter>
  );
}
