import React from 'react';
import { Routes, Route, useParams, Navigate } from 'react-router-dom';
import { Error } from 'app/components';

const mainTitle = 'Vespa App';

function TitledRoute({ element, title, default: isDefault, ...props }) {
  const params = useParams();
  const clone = React.cloneElement(element, Object.assign(props, params));
  if (title != null) {
    const titleStr = typeof title === 'function' ? title(params) : title;
    document.title = titleStr.endsWith(mainTitle)
      ? titleStr
      : `${titleStr} - ${mainTitle}`;
  } else if (isDefault) {
    // Reset the title if title is not set and this is a default router
    document.title = mainTitle;
  }

  return clone;
}

export function Router({ children }) {
  // If there is only one route then this comes as an object.
  if (!Array.isArray(children)) children = [children];
  children = children.filter(({ props }) => props.enabled ?? true);

  if (!children.some((child) => child.props.default))
    children.push(<Error code={404} default />);

  return (
    <Routes>
      {children.map(({ props, ...element }, i) => (
        <Route
          key={`${i}-${props.path}`}
          path={props.default ? '*' : props.path}
          element={
            element.type === Redirect ? (
              Object.assign({ props }, element)
            ) : (
              <TitledRoute element={element} {...props} />
            )
          }
        />
      ))}
    </Routes>
  );
}

export function Redirect({ to, replace }) {
  return <Navigate {...{ to, replace }} />;
}
