// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import React from 'react';
import { AppShell } from '@mantine/core';
import { Header } from 'app/components/layout/header';

export function Layout({ children }) {
  return <AppShell header={<Header />}>{children}</AppShell>;
}
