package org.praxislive.code.userapi;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.praxislive.core.types.PError;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class AsyncTest {

    public AsyncTest() {

    }

    @Test
    public void testComplete() {
        Async<Integer> async = new Async<>();
        assertFalse(async.done());
        assertTrue(async.complete(1));
        assertFalse(async.complete(2));
        assertTrue(async.done());
        assertFalse(async.failed());
        assertEquals(1, async.result());
        assertNull(async.error());
    }

    @Test
    public void testError() {
        Async<Integer> async = new Async<>();
        PError err1 = PError.of("ERROR1");
        PError err2 = PError.of("Error2");
        assertFalse(async.done());
        assertTrue(async.fail(err1));
        assertTrue(async.done());
        assertTrue(async.failed());
        assertFalse(async.fail(err2));
        assertEquals(err1, async.error());
        assertNull(async.result());
    }

    @Test
    public void testQueuePoll() {
        Async<Integer> async1 = new Async<>();
        Async<Integer> async2 = new Async<>();
        Async<Integer> async3 = new Async<>();
        Async<Integer> async4 = new Async<>();

        Async.Queue<Integer> queue = new Async.Queue<>();
        queue.add(async1);
        queue.add(async2);
        queue.add(async3);
        queue.add(async4);

        assertEquals(4, queue.size());
        assertNull(queue.poll());
        async3.complete(3);
        assertEquals(async3, queue.poll());
        assertNull(queue.poll());
        assertEquals(3, queue.size());

        async4.fail(PError.of("FOUR"));
        async2.complete(2);
        async1.complete(1);

        assertEquals(async1, queue.poll());
        assertEquals(async2, queue.poll());
        assertEquals(async4, queue.poll());
        assertNull(queue.poll());

    }

    @Test
    public void testQueuePollSized() {
        Async<Integer> async1 = new Async<>();
        Async<Integer> async2 = new Async<>();
        Async<Integer> async3 = new Async<>();
        Async<Integer> async4 = new Async<>();

        Async.Queue<Integer> queue = new Async.Queue<>();
        queue.add(async1);
        queue.add(async2);
        queue.add(async3);

        async3.complete(3);

        var list = queue.limit(2);
        assertEquals(1, list.size());
        assertEquals(async1, list.get(0));
        assertEquals(2, queue.size());

        var ousted = queue.add(async4);
        assertEquals(async2, ousted);

        async4.fail(PError.of("FOUR"));

        assertEquals(async3, queue.poll());
        assertEquals(async4, queue.poll());
        assertNull(queue.poll());

    }
    
    @Test
    public void testQueueHandler() {
        Async<Integer> async1 = new Async<>();
        Async<Integer> async2 = new Async<>();
        Async<Integer> async3 = new Async<>();
        Async<Integer> async4 = new Async<>();
        
        Async.Queue<Integer> queue = new Async.Queue<>();
        
        List<Integer> results = new ArrayList<>();
        List<PError> errors = new ArrayList<>();
        
        queue.onDone(results::add, errors::add);
        
        async3.complete(3);
        queue.add(async1);
        queue.add(async2);
        queue.add(async3);
        queue.add(async4);
        
        assertEquals(1, results.size());
        assertEquals(3, results.get(0));
        
        async1.complete(1);
        async2.fail(PError.of("TWO"));
        assertEquals(2, results.size());
        assertEquals(1, results.get(1));
        assertEquals(1, errors.size());
        assertEquals("TWO", errors.get(0).message());
        
        queue.clear();
        async4.complete(4);
        assertEquals(2, results.size());
        assertEquals(1, errors.size());
        
    }

}
