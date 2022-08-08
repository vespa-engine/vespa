import React from 'react';

export default function SimpleForm({
  className = 'propvalue',
  initial,
  size = '20',
  onChange,
  placeholder,
}) {
  return (
    <form className={className}>
      <input
        size={size}
        type="text"
        className={className}
        defaultValue={initial}
        onChange={onChange}
        placeholder={placeholder}
      />
    </form>
  );
}
