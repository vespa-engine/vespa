import React from 'react';
import { Header as MantineHeader } from '@mantine/core';
import { HeaderLogo } from 'app/components/layout/header-logo';

export function Header() {
  return (
    <MantineHeader
      height={55}
      sx={(theme) => ({
        display: 'flex',
        alignItems: 'center',
        paddingLeft: theme.spacing.md,
        paddingRight: theme.spacing.md,
        background: theme.cr.getSolidBackground(),
        borderBottom: `1px solid ${theme.cr.getSubtleBorderAndSeparator()}`,
      })}
    >
      <HeaderLogo />
    </MantineHeader>
  );
}
