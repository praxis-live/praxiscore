package org.praxislive.code;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.praxislive.code.userapi.Data;

import static org.junit.jupiter.api.Assertions.*;
import org.praxislive.core.Lookup;

public class DataPortTest {

    @Test
    public void testCacheRelease() {
        IntBufferDataSink sink = new IntBufferDataSink();

        Data.Pipe<int[]> head = Data.with(b -> b[0] = 1);
        DataPort.InPipe<int[]> in = new DataPort.InPipe<>();
        DataPort.OutPipe<int[]> out = new DataPort.OutPipe<>();
        Data.Pipe<int[]> extra = Data.with(b -> b[0] = 2);
        Data.Pipe<int[]> multiSource = Data.with(b -> {
        });

        out.addSource(multiSource);
        multiSource.addSource(in);
        multiSource.addSource(extra);
        in.addSource(head);
        sink.input().addSource(out);

        int[] result = sink.process(new int[1]);
        assertEquals(3, result[0]);
        assertEquals(2, sink.activeBufferCount);
        out.reset(false);
        assertEquals(0, sink.activeBufferCount);

        assertTrue(out.sources().isEmpty());
        assertEquals(List.of(sink.input()), out.sinks());
        assertTrue(in.sinks().isEmpty());
        assertEquals(List.of(head), in.sources());
        result = sink.process(new int[1]);
        assertEquals(0, result[0]);

    }

    private static class IntBufferDataSink extends Data.Sink<int[]> {

        private int activeBufferCount;

        private IntBufferDataSink() {
            onCreate(b -> {
                activeBufferCount++;
                return new int[b.length];
            });
            onClear(b -> {
                Arrays.fill(b, 0);
                return b;
            });
            onAccumulate((dst, src) -> {
                int len = Math.min(dst.length, src.length);
                for (int i = 0; i < len; i++) {
                    dst[i] += src[i];
                }
                return dst;
            });
            onValidate((dst, src) -> {
                return dst.length == src.length;
            });
            onDispose(s -> {
                activeBufferCount--;
            });
        }

        @Override
        protected void log(Exception ex) {
            throw new RuntimeException(ex);
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

    }

}
