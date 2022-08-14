import React from 'react';
import { AppShell } from '@mantine/core';
import { Header } from 'app/components/layout/header';

export function Layout({ children }) {
  return <AppShell header={<Header />}>{children}</AppShell>;
}
