import React from 'react';
import { Header as MantineHeader } from '@mantine/core';
import { HeaderLogo } from 'app/components/layout/header-logo';

export function Header() {
  return (
    <MantineHeader height={55} sx={{ display: 'flex', alignItems: 'center' }}>
      <HeaderLogo />
    </MantineHeader>
  );
}
