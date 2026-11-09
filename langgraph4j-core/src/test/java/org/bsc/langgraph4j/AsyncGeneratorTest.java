package org.bsc.langgraph4j;


import org.bsc.async.AsyncGenerator;
import org.bsc.async.v5.AsyncGeneratorFlow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
        final var countDownLatch = new java.util.concurrent.CountDownLatch(3);
        var myArray = List.of( "e1", "e2", "e3", "e4", "e5" );

        final var it = AsyncGeneratorFlow.<String>create( $1 -> {
            try {
                for( String value: myArray ) {

                    $1.dispatchSync( AsyncGenerator.Data.of( completedFuture(value )) );
                    Thread.sleep(100);

                }
                $1.dispatchSync( AsyncGenerator.Data.done() );
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        });

        final var cancelFuture = CompletableFuture.runAsync(() -> {
            try {
                countDownLatch.await();
                it.cancel(false);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        final List<String> result = new ArrayList<>();

        final var generationFuture = it.forEachAsync( value -> {
                    result.add(value);
                    countDownLatch.countDown();
                    System.out.printf("value %s added on thread[%s]%n", value, Thread.currentThread().getName());
                })
                .thenApply( GraphResult::from )
                .thenAccept( r -> {
                    assertTrue( r.isCancelled());
                    System.out.println("cancelled");
                })
                .exceptionally( ex -> {
                    System.out.println( ex.getCause().getMessage() );
                    assertInstanceOf( CancellationException.class, ex.getCause() );
                    return null;
                });

        CompletableFuture.allOf( cancelFuture, generationFuture ).join();

        var sizeAfterInterruption = result.size();
        assertEquals( 3, result.size() );

        for (String i : it) {
            result.add(i);
            System.out.println(i);
        }
        System.out.println( "Finished");

        assertEquals(sizeAfterInterruption, result.size() );

    }

    @Test
    public void asyncQueueTest() throws Exception {

        try( final var it = AsyncGeneratorFlow.<String>create( $1 -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                $1.dispatchAsync( AsyncGenerator.Data.of( completedFuture("e"+i )) );
            }
            $1.dispatchAsync( AsyncGenerator.Data.done() );
        }) ) {

            List<String> result = new ArrayList<>();

            it.forEachAsync(result::add).thenAccept((t) -> {
                System.out.println("Finished");
            }).join();


            assertEquals(10, result.size());
            assertIterableEquals(List.of("e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9"), result);
        }
    }


    @Test
    public void asyncQueueToStreamTest() throws Exception {

        try( final var it = AsyncGeneratorFlow.<String>create( $1 -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                $1.dispatchAsync( AsyncGenerator.Data.of( completedFuture("e"+i )) );
            }
            $1.dispatchAsync( AsyncGenerator.Data.done() );
        }) ) {

            java.util.stream.Stream<String> result = it.stream();

            java.util.Optional<String> lastElement = result.reduce((a, b) -> b);

            assertTrue(lastElement.isPresent());
            assertEquals("e9", lastElement.get());
        }
    }

    @Test
    public void asyncQueueIteratorExceptionTest() throws Exception {

        try( final var it = AsyncGeneratorFlow.<String>create( $1 -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                $1.dispatchAsync(AsyncGenerator.Data.of(completedFuture("e" + i)));

                if (i == 2) {
                    $1.dispatchAsync(AsyncGenerator.Data.error(new RuntimeException("error test")));
                }
            }

        }) ) {

            java.util.stream.Stream<String> result = it.stream();

            assertThrows(Exception.class, () -> result.reduce((a, b) -> b));
        }

    }

    @Test
    public void asyncQueueForEachExceptionTest() throws Exception {

        try( final var it = AsyncGeneratorFlow.<String>create( $1 -> {
            for( int i = 0 ; i < 10 ; ++i ) {
                $1.dispatchAsync(AsyncGenerator.Data.of(completedFuture("e" + i)));

                if (i == 2) {
                    $1.dispatchAsync(AsyncGenerator.Data.error(new RuntimeException("error test")));
                }
            }

        }) ) {

            assertThrows(Exception.class, () -> it.forEachAsync(System.out::println).get());
        }

    }

}
