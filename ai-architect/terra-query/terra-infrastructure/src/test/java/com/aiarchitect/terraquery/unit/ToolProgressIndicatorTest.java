package com.aiarchitect.terraquery.unit;

import com.aiarchitect.terraquery.streaming.ChatEvent;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ToolProgressIndicatorTest {

    @Test
    void activateRoutesEmissionsToBoundSink() throws Exception {
        ToolProgressIndicator indicator = new ToolProgressIndicator();
        Sinks.Many<ChatEvent> sink = Sinks.many().multicast().onBackpressureBuffer(16, false);
        List<ChatEvent> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        sink.asFlux().subscribe(e -> {
            received.add(e);
            latch.countDown();
        });

        indicator.activate(sink);
        indicator.toolCallStarted("DataRetrievalAgent", "get_deadliest_disasters");
        indicator.deactivate();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).singleElement()
                .extracting(ChatEvent::type)
                .isEqualTo(ChatEvent.EventType.TOOL_CALL_START);
    }

    @Test
    void deactivateStopsRouting() throws Exception {
        ToolProgressIndicator indicator = new ToolProgressIndicator();
        Sinks.Many<ChatEvent> sink = Sinks.many().multicast().onBackpressureBuffer(16, false);

        indicator.activate(sink);
        indicator.deactivate();
        indicator.toolCallStarted("DataRetrievalAgent", "query_disasters");

        List<ChatEvent> received = new ArrayList<>();
        sink.asFlux().subscribe(received::add);
        Thread.sleep(100);
        assertThat(received).isEmpty();
    }
}
