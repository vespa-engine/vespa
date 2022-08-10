import React from 'react';
import { Global, MantineProvider } from '@mantine/core';
import { Colors } from 'app/styles/theme/colors';
import { styles } from 'app/styles/global';
import { getTheme } from 'app/styles/theme';

function setColorResolver(theme) {
  if (!theme.cr) theme.cr = new Colors(theme);
  return theme;
}

export function ThemeProvider({ children }) {
  return (
    <MantineProvider theme={getTheme()}>
      <Global styles={(theme) => styles(setColorResolver(theme))} />
      {children}
    </MantineProvider>
  );
}
