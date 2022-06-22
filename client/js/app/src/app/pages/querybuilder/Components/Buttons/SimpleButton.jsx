import React from 'react';

export default function SimpleButton({ onClick, children, className, id }) {
  return (
    <button id={id} className={className} onClick={onClick}>
      {children}
    </button>
  );
}
