import React from 'react';
import { Redirect, Router } from '@reach/router';
import { Error } from 'app/components';

const mainTitle = 'Vespa Console';

function AppRoute({ element, title, default: isDefault, ...props }) {
  const clone = React.cloneElement(element, props, props.children);
  if (title != null) {
    const titleStr = typeof title === 'function' ? title(props) : title;
    document.title = titleStr.endsWith(mainTitle)
      ? titleStr
      : `${titleStr} - ${mainTitle}`;
  } else if (isDefault) {
    // Reset the title if title is not set and this is a default router
    document.title = mainTitle;
  }
  return clone;
}

export function AppRouter({ children, props: inParentProps }) {
  const newProps = Object.assign(
    { primary: false, component: React.Fragment },
    inParentProps
  );

  // If there is only one route then this comes as an object.
  if (!Array.isArray(children)) children = [children];
  const hasDefault = children.some((child) => child.props.default);

  return (
    <Router {...newProps}>
      {children
        .filter(({ props }) => props.enabled ?? true)
        .map((e, i) => {
          if (e.type === Redirect) return e;
          return <AppRoute key={i} element={e} {...e.props} />;
        })}
      {!hasDefault && <Error code={404} default />}
    </Router>
  );
}
