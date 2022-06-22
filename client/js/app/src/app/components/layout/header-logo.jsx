import React from 'react';
import { Image } from '@mantine/core';
import { VespaLogo } from 'app/assets';

export function HeaderLogo() {
  return <Image height={34} src={VespaLogo} />;
}
