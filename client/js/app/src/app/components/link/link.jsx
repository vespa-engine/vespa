// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import React from 'react';
import { Link as InternalLink } from 'react-router-dom';
import { Anchor } from '@mantine/core';

export const isInternalLink = (link) => {
  if (!link) return false;
  return !/^[a-z]+:\/\//.test(link);
};

export function Link({ to, api = false, ...props }) {
  const internal = !api && isInternalLink(to);

  if (!props.download && to && internal)
    return <Anchor component={InternalLink} to={to} {...props} />;

  const fixedProps = Object.assign(
    to ? { href: (api ? window.config.api : '') + to } : {},
    to && !internal && { target: '_blank', rel: 'noopener noreferrer' },
    props
  );

  return <Anchor {...fixedProps} />;
}
