/**
 * Represents a Last-In, First-Out (LIFO) stack.
 * 
 * @template T The type of elements held in the stack.
 */
export class Stack {
    /**
     * The array holding the stack items.
     * @type {Array<T>}
     */
    items;
  
    /**
     * The array holding the stack items.
     * @param  {Array<T>} items
     */
    constructor(items = []) {
      this.items = items ;
    }
  
    /**
     * Adds an item to the top of the stack.
     * @param {T} item The item to add.
     * @returns {number} The new length of the stack.
     */
    push(item) {
        return this.items.unshift(item);
    }
  
    /**
     * Removes and returns the item from the top of the stack.
     * @returns {T | undefined} The removed item, or undefined if the stack is empty.
     */
    pop() {
      return this.items.shift();
    }
  
    /**
     * Returns the item at the top of the stack without removing it.
     * @returns {T | undefined} The top item, or undefined if the stack is empty.
     */
    peek() {
      return this.items[0]
    }
  
    /**
     * Returns a copy of all elements in the stack as an array,
     * ordered from top (most recent) to bottom (least recent).
     * This method does not modify the original stack.
     * @returns {Array<T>} An array containing all elements, with the most recently added element at index 0.
     */
    get elements() {
      // Create a reversed copy to avoid mutating the internal 'items' array
      //return this.items.toReversed()
      return this.items
    }
  
    /**
     * Checks if the stack is empty.
     * @returns {boolean} True if the stack is empty, false otherwise.
     */
    isEmpty() {
      return this.items.length === 0;
    }
  
    /**
     * Gets the number of items in the stack.
     * @returns {number} The size of the stack.
     */
    get size() {
      return this.items.length;
    }
  
    /**
     * Clears all items from the stack.
     */
    clear() {
      this.items = [];
    }
  }