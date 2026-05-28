package org.bsc.langgraph4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GraphPathTest {

    @Test
    public void evaluate() {

        var path = GraphPath.of( "");

        assertEquals( GraphPath.empty(), path );

        path = GraphPath.of( );

        assertTrue( path.isEmpty() );
        assertEquals( 0, path.elementCount() );
        assertEquals( GraphPath.empty(), path );
        assertEquals( GraphPath.empty(), path.parent() );

        path = path.append( "path1" );
        assertEquals( 1, path.elementCount() );
        assertEquals( "path1", path.elementAt(0) );
        assertTrue(  path.rootElement().isPresent() );
        assertEquals(  "path1", path.rootElement().get() );

        var path2 = path.append( "path 2" );
        assertEquals( 2, path2.elementCount() );
        assertEquals(  "path1/path 2", path2.toString() );
        assertEquals( path, path2.parent() );
        assertEquals( path, path2.root() );
        assertEquals( path2, GraphPath.of( "path1", "path 2" ) );


        assertThrowsExactly( IllegalArgumentException.class,
                () -> GraphPath.of("path 3/path5" ));
        assertThrowsExactly( IllegalArgumentException.class,
                () -> path2.append("path 3/ path4" ));
    }

    @Test
    public void replaceLast() {

        var path = GraphPath.of("path1", "path2");
        var replaced = path.replaceLast("path3");

        assertEquals(GraphPath.of("path1", "path3"), replaced);
        assertEquals(GraphPath.of("path1", "path2"), path);

        assertEquals(GraphPath.of("path1"), GraphPath.empty().replaceLast("path1"));
        assertSame(path, path.replaceLast(null));
        assertSame(path, path.replaceLast(""));

        assertThrowsExactly(IllegalArgumentException.class,
                () -> path.replaceLast("path 3/path4"));
    }
}
