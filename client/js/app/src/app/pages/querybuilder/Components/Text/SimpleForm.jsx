import React from 'react';
import { useState } from 'react';

export default function SimpleForm({
  id,
  className = 'propvalue',
  initial,
  size = '20',
  onChange,
}) {
  SimpleForm.defaultProps = {
    onChange: handleChange,
  };
  const [input, setValue] = useState(initial);

  function handleChange(e) {
    setValue(e.target.value);
  }

  return (
    <form className={className} id={id}>
      <input
        size={size}
        type="text"
        id={id}
        className={className}
        defaultValue={initial}
        onChange={onChange}
      />
    </form>
  );
}
