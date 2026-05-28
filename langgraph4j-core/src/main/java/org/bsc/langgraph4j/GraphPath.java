package org.bsc.langgraph4j;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Represents a path in a graph, consisting of a sequence of string elements.
 * This class is immutable and provides methods for path manipulation and traversal.
 * <p>
 * The path elements are separated by {@value #DELIMITER}.
 * </p>
 */
public final class GraphPath implements Iterable<String> {

    /**
     * The delimiter character used to separate path elements.
     */
    public static final char DELIMITER = '/';
    private static final String DELIMITER_STR = String.valueOf(DELIMITER);

    private static final GraphPath EMPTY = new GraphPath(List.of());

    /**
     * Returns an empty GraphPath.
     *
     * @return the empty path.
     */
    public static GraphPath empty() {
        return EMPTY;
    }

    /**
     * Creates a GraphPath from the given elements.
     * <p>
     * Null or empty strings in the input are ignored.
     * </p>
     *
     * @param elements the elements to include in the path.
     * @return a new GraphPath containing the valid elements.
     * @throws IllegalArgumentException if an element contains the delimiter character.
     */
    public static GraphPath of(String... elements) {

        if (elements == null) return EMPTY;

        final var normalized = Arrays.stream(elements)
                .filter(e -> e != null && !e.isEmpty())
                .peek(GraphPath::validateElement)
                .toList();

        return normalized.isEmpty() ? EMPTY : new GraphPath(normalized);
    }

    private final LinkedList<String> elements;

    private GraphPath(List<String> elements) {
        this.elements = new LinkedList<>(elements);
    }

    private static void validateElement( String element ) {
        if( element.contains( DELIMITER_STR ) )
            throw new IllegalArgumentException( "path element cannot include symbol '%c'".formatted(DELIMITER));
    }

    /**
     * Checks if the path is empty.
     *
     * @return true if the path has no elements, false otherwise.
     */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /**
     * Returns the number of elements in the path.
     *
     * @return the number of elements.
     */
    public int elementCount() {
        return elements.size();
    }

    /**
     * Returns the parent path (the path without the last element).
     * <p>
     * If the path is empty or has only one element, returns an empty path.
     * </p>
     *
     * @return the parent path.
     */
    public GraphPath parent() {
        if (elements.isEmpty() || elements.size() == 1) return EMPTY;
        return new GraphPath(elements.subList(0, elements.size() - 1));
    }

    /**
     * Returns the root path (a path containing only the first element).
     * <p>
     * If the path is empty, returns an empty path.
     * </p>
     *
     * @return the root path.
     */
    public GraphPath root() {
        if (elements.isEmpty()) return EMPTY;
        return new GraphPath(List.of(elements.getFirst()));
    }

    /**
     * Checks if this path starts with the given prefix path.
     *
     * @param prefix the prefix path to check.
     * @return true if this path starts with the prefix, false otherwise.
     * @throws NullPointerException if prefix is null.
     */
    public boolean startsWith( GraphPath prefix) {
        requireNonNull(prefix, "prefix cannot be null");
        if (prefix.elements.size() > this.elements.size())
            return false;

        for (int i = 0; i < prefix.elements.size(); ++i) {
            if (!Objects.equals(this.elements.get(i), prefix.elements.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the first element of the path.
     *
     * @return an Optional containing the first element, or empty if the path is empty.
     */
    public Optional<String> rootElement() {
        return elements.isEmpty() ?
                Optional.empty() :
                Optional.of(elements.getFirst());
    }

    /**
     * Returns the last element of the path.
     *
     * @return an Optional containing the last element, or empty if the path is empty.
     */
    public Optional<String> lastElement() {
        return elements.isEmpty() ?
                Optional.empty() :
                Optional.of(elements.getLast());
    }

    /**
     * Returns the element at the specified position in the path.
     *
     * @param index the index of the element to return.
     * @return the element at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of range.
     */
    public String elementAt(int index) {
        return elements.get(index);
    }

    /**
     * Returns an unmodifiable list of the elements in this path.
     *
     * @return the list of elements.
     */
    public List<String> elements() {
        return List.copyOf(elements);
    }

    /**
     * Appends an element to the end of this path.
     *
     * @param element the element to append.
     * @return a new GraphPath with the appended element.
     * @throws IllegalArgumentException if the element contains the delimiter character.
     */
    public GraphPath append(String element) {
        if (element == null || element.isEmpty()) return this;

        validateElement(element);


        var newElements = new ArrayList<String>(elements.size() + 1);
        newElements.addAll(elements);
        newElements.add(element);
        return new GraphPath(newElements);
    }

    public GraphPath replaceLast(String element) {
        if (element == null || element.isEmpty()) return this;

        validateElement(element);

        if( elements.isEmpty() ) {
            return GraphPath.of(element);
        }

        final var newElements = new ArrayList<>(elements);
        newElements.set(elements.size() - 1, element);

        return new GraphPath(newElements);
    }

    /**
     * Returns a sequential Stream with this path's elements as its source.
     *
     * @return a Stream of elements.
     */
    public Stream<String> stream() {
        return elements.stream();
    }

    /**
     * Returns an iterator over the elements in this path.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<String> iterator() {
        return elements.iterator();
    }

    /**
     * Returns the string representation of the path.
     * <p>
     * The elements are joined by the delimiter {@value #DELIMITER}.
     * </p>
     *
     * @return the string representation.
     */
    @Override
    public String toString() {
        if (elements.isEmpty()) return "";
        return String.join(DELIMITER_STR, elements);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphPath other)) return false;
        return elements.equals(other.elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

}
