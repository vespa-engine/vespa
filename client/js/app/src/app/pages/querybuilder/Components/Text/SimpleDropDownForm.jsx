import React, { useContext, useEffect } from 'react';
import { QueryInputContext } from '../Contexts/QueryInputContext';

export default function SimpleDropDownForm({
  choices,
  id,
  className = 'input',
  onChange,
  value,
}) {
  const { selectedItems } = useContext(QueryInputContext);

  //FIXME: using the filtered list to render options results in dropdown not changing the displayed selection to what was actually selected.
  let filtered = Object.keys(choices).filter(
    (choice) => !selectedItems.includes(choice)
  );
  useEffect(() => {
    filtered = Object.keys(choices).filter(
      (choice) => !selectedItems.includes(choice)
    );
  }, [selectedItems]);

  const options = Object.keys(choices).map((choice) => {
    return (
      <option className="options" key={choice} value={choices[choice].name}>
        {choices[choice].name}
      </option>
    );
  });

  return (
    <form id={id}>
      <select className={className} id={id} value={value} onChange={onChange}>
        {options}
      </select>
    </form>
  );
}
