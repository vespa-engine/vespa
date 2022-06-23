import { useSetState } from '@mantine/hooks';
import React, { useEffect } from 'react';
import { useState } from 'react';

export default function SimpleDropDownForm({
  choices,
  id,
  className = 'input',
  onChange,
}) {
  SimpleDropDownForm.defaultProps = {
    onChange: handleChange,
  };
  const { choice, setChoice } = useState(choices[0]);

  const options = Object.keys(choices).map((choice) => {
    return (
      <option className="options" key={choice} value={choices[choice].name}>
        {choices[choice].name}
      </option>
    );
  });

  function handleChange(e) {
    setChoice(e.target.value);
  }

  return (
    <form id={id}>
      <select className={className} id={id} value={choice} onChange={onChange}>
        {options}
      </select>
    </form>
  );
}
