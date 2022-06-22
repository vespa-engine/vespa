import React from 'react';
import { Center } from '@mantine/core';

// TODO: make a better page

function getMessage(code, location) {
  const statusCode =
    parseInt(code || new URLSearchParams(location?.search).get('code')) || 404;

  switch (statusCode) {
    case 403:
      return 'Sorry, you are not authorized to view this page.';
    case 404:
      return 'Sorry, the page you were looking for does not exist.';
    case 500:
      return 'Oops... something went wrong.';
    default:
      return 'Unknown error - really, I have no idea what is going on here.';
  }
}

export function Error({ code, location }) {
  const message = getMessage(code, location);
  return <Center sx={{ minHeight: '89px' }}>{message}</Center>;
}
