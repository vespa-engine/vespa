import React from 'react';
import { Container, Link, CardLink } from 'app/components';

export function Home() {
  return (
    <Container>
      <CardLink component={Link} to="/querybuilder">
        query builder
      </CardLink>
    </Container>
  );
}
