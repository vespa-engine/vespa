import React from 'react';
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
  const { choice, setChoice } = useState();

  const options = choices.map((value, index) => {
    return (
      <option className="options" key={index} value={value}>
        {value}
      </option>
    );
  });

  function handleChange(e) {
    setChoice(e.target.value);
  }

  return (
    <form id={id}>
      <select className={className} value={choice} onChange={onChange}>
        {options}
      </select>
    </form>
  );
}
