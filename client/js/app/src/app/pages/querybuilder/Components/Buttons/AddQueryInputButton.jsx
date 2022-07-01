import React, { useContext } from 'react';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import OverlayTrigger from 'react-bootstrap/OverlayTrigger';
import Tooltip from 'react-bootstrap/Tooltip';

export default function AddQueryInput() {
  const { inputs, setInputs, id, setId } = useContext(QueryInputContext);

  /**
   * Adds a new element to inputs.
   * @param {Event} e Event that happened.
   */
  const updateInputs = (e) => {
    e.preventDefault();
    setId((id) => id + 1);
    setInputs((prevInputs) => [
      ...prevInputs,
      {
        id: `${id + 1}`,
        type: 'yql',
        typeof: 'String',
        input: '',
        hasChildren: false,
        children: [],
      },
    ]);
  };

  return (
    <OverlayTrigger
      placement="right"
      delay={{ show: 250, hide: 400 }}
      overlay={<Tooltip id="button-tooltip">Add row</Tooltip>}
    >
      <span>
        <button
          id="addRow"
          className="addRow"
          height="0"
          width="0"
          onClick={updateInputs}
        >
          +
        </button>
      </span>
    </OverlayTrigger>
  );
}
