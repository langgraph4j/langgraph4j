package org.bsc.langgraph4j;


import org.bsc.async.AsyncGenerator;
import org.bsc.async.AsyncGeneratorQueue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.*;

public class AsyncGeneratorTest {

    @Test
    public void asyncForEachAsyncTest() throws Exception {

        var myArray = List.of( "e1", "e2", "e3", "e4", "e5" );

        final var it = AsyncGenerator.from(myArray.iterator());

        List<String> result = new ArrayList<>();

        it.forEachAsync( result::add ).thenAccept( t -> {
            System.out.println( "Finished");
        }).join();

        for (String i : it) {
            result.add(i);
            System.out.println(i);
        }
        System.out.println( "Finished");

        assertEquals(myArray.size(), result.size() );
        assertIterableEquals( myArray, result );
    }

    @Test
    public void asyncForEachAsyncCancelTest() throws Exception {

        var myArray = List.of( "e1", "e2", "e3", "e4", "e5" );

        final var it = new AsyncGenerator.BaseCancellable<String>() {

            private int cursor = 0;
            @Override
            public Data<String> next() {

                if (cursor == myArray.size()) {
                    return Data.done();
                }

                return Data.of(completedFuture(myArray.get(cursor++)));
            }
        };

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(400);
                it.cancel(true);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        final List<String> result = new ArrayList<>();

        it.forEachAsync( value -> {
                    try {
                        Thread.sleep(100);
                        result.add(value);
                        System.out.println(value);
                    } catch (InterruptedException e) {
                        throw new CancellationException( "Cancelled" );
                    }
                })
                .thenAccept( t -> {
                    fail( "Should not reach end of iteration");
                    System.out.println( "Finished");
                })
                .exceptionally( ex -> {
                    assertInstanceOf( CancellationException.class, ex.getCause() );
                    System.out.println( ex.getCause().getMessage() );
                    return null;
                })
                .join();

        var sizeAfterInterruption = result.size();
        assertNotEquals( myArray.size(), result.size() );

        for (String i : it) {
            result.add(i);
            System.out.println(i);
        }
        System.out.println( "Finished");

        assertEquals(sizeAfterInterruption, result.size() );

    }

    @Test
    public void asyncQueueTest() throws Exception {

        final AsyncGenerator<String> it = AsyncGeneratorQueue.of(new LinkedBlockingQueue<>(), queue -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                queue.add( AsyncGenerator.Data.of( completedFuture("e"+i )) );
            }
        });

        List<String> result = new ArrayList<>();

        it.forEachAsync(result::add).thenAccept( (t) -> {
            System.out.println( "Finished");
        }).join();


        assertEquals( 10, result.size());
        assertIterableEquals(List.of("e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9"), result);

    }


    @Test
    public void asyncQueueToStreamTest() throws Exception {

        // AsyncQueue initialized with a direct executor. No thread is used on next() invocation
        final AsyncGenerator<String> it = AsyncGeneratorQueue.of( new LinkedBlockingQueue<AsyncGenerator.Data<String>>(), queue -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                queue.add( AsyncGenerator.Data.of( completedFuture("e"+i )) );
            }
        });

        java.util.stream.Stream<String> result = it.stream();

        java.util.Optional<String> lastElement =   result.reduce((a, b) -> b);

        assertTrue( lastElement.isPresent());
        assertEquals( "e9",lastElement.get() );

    }

    @Test
    public void asyncQueueIteratorExceptionTest() throws Exception {

        final AsyncGenerator<String> it = AsyncGeneratorQueue.of( new LinkedBlockingQueue<AsyncGenerator.Data<String>>(), queue -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                queue.add( AsyncGenerator.Data.of( completedFuture("e"+i )) );

                if( i == 2 ) {
                    throw new RuntimeException("error test");
                }
            }

        });

        java.util.stream.Stream<String> result = it.stream();

        assertThrows( Exception.class,  () -> result.reduce((a, b) -> b ));

    }

    @Test
    public void asyncQueueForEachExceptionTest() throws Exception {

        final AsyncGenerator<String> it = AsyncGeneratorQueue.of( new LinkedBlockingQueue<AsyncGenerator.Data<String>>(), queue -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                queue.add( AsyncGenerator.Data.of( completedFuture("e"+i )) );

                if( i == 2 ) {
                    throw new RuntimeException("error test");
                }
            }

        });

        assertThrows( Exception.class, () -> it.forEachAsync( System.out::println ).get() );

    }

}
