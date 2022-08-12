import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { NotificationsProvider as MantineNotificationsProvider } from '@mantine/notifications';
import { Layout } from 'app/components';
import { Home } from 'app/pages/home/home';
import { QueryBuilder } from 'app/pages/querybuilder';
import { QueryTracer } from 'app/pages/querytracer/query-tracer';
import { ThemeProvider } from 'app/libs/theme-provider';
import { Router } from 'app/libs/router';

export function App() {
  return (
    <BrowserRouter>
      <ThemeProvider>
        <MantineNotificationsProvider>
          <Layout>
            <Router>
              <Home path="/" title="Home" />
              <QueryBuilder path="querybuilder" title="Query Builder" />
              <QueryTracer path="querytracer" title="Query Tracer" />
            </Router>
          </Layout>
        </MantineNotificationsProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
