package com.appoptics.metrics.reporter;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.appoptics.metrics.client.Tag;
import org.junit.Test;

import java.util.ArrayList;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class AppopticsTest {
    MetricRegistry registry = new MetricRegistry();

    @Test(expected = RuntimeException.class)
    public void testErrorsOnSameNameDifferentTypes() throws Exception {
        Appoptics.metric(registry, "foo").timer();
        Appoptics.metric(registry, "foo").histogram();
    }

    @Test
    public void testReturnsSameMetric() throws Exception {
        Gauge<Integer> gauge = new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return 42;
            }
        };

        assertThat(Appoptics.metric(registry, "gauge").gauge(gauge))
                .isSameAs(Appoptics.metric(registry, "gauge").gauge(gauge));

        assertThat(Appoptics.metric(registry, "foo").timer())
                .isSameAs(Appoptics.metric(registry, "foo").timer());

        assertThat(Appoptics.metric(registry, "bar").tag("region", "us-east-1").histogram()).isSameAs(
                Appoptics.metric(registry, "bar").tag("region", "us-east-1").histogram());
    }

    @Test
    public void testNoNameConversion() throws Exception {
        Appoptics.metric(registry, "gauge").gauge(new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return 42;
            }
        });
        assertThat(Signal.decode(registry.getGauges().keySet().iterator().next())).isEqualTo(
                new Signal("gauge"));

        Appoptics.metric(registry, "counter").counter();
        assertThat(Signal.decode(registry.getCounters().keySet().iterator().next())).isEqualTo(
                new Signal("counter"));

        Appoptics.metric(registry, "histogram").histogram();
        assertThat(Signal.decode(registry.getHistograms().keySet().iterator().next())).isEqualTo(
                new Signal("histogram"));

        Appoptics.metric(registry, "meter").meter();
        assertThat(Signal.decode(registry.getMeters().keySet().iterator().next())).isEqualTo(
                new Signal("meter"));

        Appoptics.metric(registry, "timer").timer();
        assertThat(Signal.decode(registry.getTimers().keySet().iterator().next())).isEqualTo(
                new Signal("timer"));
    }

    @Test
    public void testTagsConversion() throws Exception {
        Appoptics.metric(registry, "foo")
                .tag("region", "us-east-1")
                .doNotInheritTags()
                .counter();
        Signal signal = Signal.decode(registry.getCounters().keySet().iterator().next());
        assertThat(signal).isEqualTo(new Signal(
                "foo",
                asList(new Tag("region", "us-east-1")),
                true));
    }

    @Test
    public void testRemoveTaggedMeter() {
        Appoptics.metric(registry, "test")
                .tag("foo", "bar")
                .tag("bar", "baz")
                .meter();

        Signal signal = Signal.decode(registry.getMeters().keySet().iterator().next());
        assertThat(signal).isEqualTo(new Signal(
                "test",
                asList(new Tag("foo", "bar"), new Tag("bar", "baz")),
                false));

        boolean removed = Appoptics.metric(registry, "test")
                .tag("foo", "bar")
                .tag("bar", "baz")
                .remove();

        assertThat(removed).isTrue();
        assertThat(registry.getMeters().keySet()).isEmpty();
    }

    @Test
    public void testRemoveMeter() {
        Appoptics.metric(registry, "test")
                .meter();

        boolean removed = Appoptics.metric(registry, "test").remove();

        assertThat(removed).isTrue();
        assertThat(registry.getMeters().keySet()).isEmpty();
    }

    @Test
    public void testRemoveNonExistentMetric() {
        boolean removed = Appoptics.metric(registry, "test").remove();
        assertThat(removed).isFalse();
    }
}
