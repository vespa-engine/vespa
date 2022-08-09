import React from 'react';

export default function SimpleDropDownForm({
  options,
  value,
  className = 'input',
  ...props
}) {
  // TODO: Filter selected
  return (
    <select className={className} {...props} value={value}>
      {Object.values(options).map(({ name }) => (
        <option className="options" key={name} value={name}>
          {name}
        </option>
      ))}
    </select>
  );
}
