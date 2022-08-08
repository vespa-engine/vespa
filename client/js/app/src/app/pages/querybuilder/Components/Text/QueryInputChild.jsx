import React, { useContext } from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import AddPropertyButton from '../Buttons/AddPropertyButton';
import SimpleButton from '../Buttons/SimpleButton';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import QueryDropdownForm from './QueryDropDownForm';
import SimpleForm from './SimpleForm';
import { childMap } from 'app/pages/querybuilder/parameters';

export default function QueryInputChild({ id }) {
  const { inputs, setInputs } = useContext(QueryInputContext);

  let index = inputs.findIndex((element) => element.id === id);
  let childArray = inputs[index].children;
  let currentTypes = inputs[index].type;

  /**
   * Update the state of inputs to reflect what is written into the form.
   * @param {Event} e Event containing the new input.
   */
  const updateInput = (e) => {
    e.preventDefault();
    let newInputs = inputs.slice();
    let iterId = e.target.id.replace('v', '');
    let currentId = iterId.substring(0, 1);
    let index = newInputs.findIndex((element) => element.id === currentId);
    let children = newInputs[index].children;
    const traversedChildren = traverseChildren(iterId, children, '');
    children = traversedChildren.children;
    index = children.findIndex((element) => element.id === iterId);
    children[index].input = e.target.value;
    setInputs(newInputs);
  };

  /**
   * Returns a placeholder text for a SimpleForm component.
   * @param {String} id The id of the SimpleForm component.
   * @returns {String} The placeholder text
   */
  const setPlaceHolder = (id) => {
    let currentId = id.substring(0, 1);
    let index = inputs.findIndex((element) => element.id === currentId);
    let combinedType = inputs[index].type;
    let children = inputs[index].children;
    if (id.length > 3) {
      const traversedChildren = traverseChildren(id, children, combinedType);
      combinedType = traversedChildren.combinedType;
      children = traversedChildren.children;
      const currentChoice = childMap[combinedType];
      index = children.findIndex((element) => element.id === id);
      combinedType = children[index].type;
      return currentChoice[combinedType].type;
    } else {
      const currentChoice = childMap[combinedType];
      index = children.findIndex((element) => element.id === id);
      combinedType = children[index].type;
      return currentChoice[combinedType].type;
    }
  };

  /**
   * Removes the row with the provided id.
   * @param {String} id Id of row.
   */
  const removeRow = (id) => {
    let newInputs = inputs.slice();
    let currentId = id.substring(0, 1);
    let index = newInputs.findIndex((element) => element.id === currentId);
    let children = newInputs[index].children;
    const traversedChildren = traverseChildren(id, children, '');
    index = traversedChildren.children.findIndex((element) => element === id);
    traversedChildren.children.splice(index, 1);
    setInputs(newInputs);
  };

  /**
   * Traverses the children until a child with the provided id is reached.
   * @param {String} id Id of the innermost child.
   * @param {Array} children Array containing serveral child objects.
   * @param {String} combinedType The combined type of all traversed children
   * @returns {Object} An object containing the children of the child with the provided id and the combined type.
   */
  function traverseChildren(id, children, combinedType) {
    let currentId;
    let index;
    for (let i = 3; i < id.length; i += 2) {
      currentId = id.substring(0, i);
      index = children.findIndex((element) => element.id === currentId);
      combinedType = combinedType + '_' + children[index].type;
      children = children[index].children;
    }
    return { children: children, combinedType: combinedType };
  }

  const inputList = childArray.map((child) => {
    return (
      <div key={child.id} id={child.id}>
        {
          //child.id == '4.1' && console.log(child.type)
        }
        <QueryDropdownForm
          choices={childMap[currentTypes]}
          id={child.id}
          child={true}
          inital={child.type}
        />
        {child.hasChildren ? (
          <>
            <AddPropertyButton id={child.id} />
          </>
        ) : (
          <SimpleForm
            id={`v${child.id}`}
            size="30"
            onChange={updateInput}
            placeholder={setPlaceHolder(child.id)}
            inital={child.input}
          />
        )}
        <OverlayTrigger
          placement="right"
          delay={{ show: 250, hide: 400 }}
          overlay={<Tooltip id="button-tooltip">Remove row</Tooltip>}
        >
          <span>
            <SimpleButton
              id={`b${child.id}`}
              className="removeRow"
              onClick={() => removeRow(child.id)}
            >
              -
            </SimpleButton>
          </span>
        </OverlayTrigger>
        <br />
        <Child
          type={currentTypes + '_' + child.type}
          child={child}
          onChange={updateInput}
          placeholder={setPlaceHolder}
          removeRow={removeRow}
        />
      </div>
    );
  });

  return <>{inputList}</>;
}

function Child({ child, type, onChange, placeholder, removeRow }) {
  const nestedChildren = (child.children || []).map((child) => {
    return (
      <div key={child.id}>
        <QueryDropdownForm
          choices={childMap[type]}
          id={child.id}
          child={true}
          initial={child.type}
        />
        {child.hasChildren ? (
          <>
            <AddPropertyButton id={child.id} />
          </>
        ) : (
          <SimpleForm
            id={`v${child.id}`}
            size="30"
            onChange={onChange}
            placeholder={placeholder(child.id)}
            initial={child.input}
          />
        )}
        <OverlayTrigger
          placement="right"
          delay={{ show: 250, hide: 400 }}
          overlay={<Tooltip id="button-tooltip">Remove row</Tooltip>}
        >
          <span>
            <SimpleButton
              id={`b${child.id}`}
              className="removeRow"
              onClick={() => removeRow(child.id)}
            >
              -
            </SimpleButton>
          </span>
        </OverlayTrigger>
        <br />
        <Child
          child={child}
          id={child.id}
          type={type + '_' + child.type}
          onChange={onChange}
          placeholder={placeholder}
          removeRow={removeRow}
        />
      </div>
    );
  });

  return <>{nestedChildren}</>;
}
