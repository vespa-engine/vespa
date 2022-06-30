import React from 'react';
import { Image } from '@mantine/core';
import { Link } from 'react-router-dom';
import { VespaLogo } from 'app/assets';

export function HeaderLogo() {
  return (
    <Link to="/">
      <Image height={34} src={VespaLogo} />
    </Link>
  );
}
