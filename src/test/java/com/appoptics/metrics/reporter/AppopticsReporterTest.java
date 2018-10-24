package com.appoptics.metrics.reporter;

import com.codahale.metrics.*;
import com.appoptics.metrics.client.*;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AppopticsReporterTest {
    MetricRegistry registry = new MetricRegistry();
    AppopticsClient client = mock(AppopticsClient.class);
    ReporterAttributes atts = new ReporterAttributes();
    ArgumentCaptor<Measures> captor = ArgumentCaptor.forClass(Measures.class);

    @Before
    public void setUp() throws Exception {
        atts.appopticsClientFactory = new IAppopticsClientFactory() {
            public AppopticsClient build(ReporterAttributes atts) {
                return client;
            }
        };
        when(client.postMeasures(captor.capture()))
                .thenReturn(new PostMeasuresResult());
        atts.durationConverter = new DurationConverter() {
            @Override
            public double convertDuration(double duration) {
                return duration;
            }
        };
        atts.rateConverter = new RateConverter() {
            @Override
            public double convertRate(double rate) {
                return rate;
            }
        };
    }

    private void report(AppopticsReporter reporter) {
        reporter.report(registry.getGauges(),
                registry.getCounters(),
                registry.getHistograms(),
                registry.getMeters(),
                registry.getTimers());
    }

    @Test
    public void testOmitsTagsAtTheRootLevel() throws Exception {
        atts.tags.add(new Tag("root", "tag"));

        Appoptics.metric(registry, "foo").tag("foo", "bar").counter().inc();
        AppopticsReporter reporter = new AppopticsReporter(atts);
        report(reporter);
        Measures captured = captor.getValue();
        HashSet<IMeasure> measures = new HashSet<IMeasure>(captured.getMeasures());
        assertThat(captured.getTags()).isEmpty();
        assertThat(measures).containsOnly(
                new Measure("foo", 1, new Tag("foo", "bar"), new Tag("root", "tag")));
    }

    @Test
    public void testCounter() throws Exception {
        Counter counter = new Counter();
        registry.register("foo", counter);
        counter.inc();
        AppopticsReporter reporter = new AppopticsReporter(atts);
        report(reporter);
        HashSet<IMeasure> measures = new HashSet<IMeasure>(captor.getValue().getMeasures());
        assertThat(measures).containsOnly(
                new Measure("foo", 1));
    }

    @Test
    public void testRejectsNonTaggedMetrics() throws Exception {
        Appoptics.metric(registry, "foo").counter().inc();
        AppopticsReporter reporter;
        reporter = new AppopticsReporter(atts);
        report(reporter);
        HashSet<IMeasure> measures;
        measures = new HashSet<IMeasure>(captor.getValue().getMeasures());
        assertThat(measures.isEmpty()).isFalse();
    }

    @Test
    public void testTaggedCounter() throws Exception {
        Counter counter = Appoptics.metric(registry, "foo").tag("a", "b").counter();
        counter.inc();
        AppopticsReporter reporter = new AppopticsReporter(atts);
        report(reporter);
        HashSet<IMeasure> measures = new HashSet<IMeasure>(captor.getValue().getMeasures());
        assertThat(measures).containsOnly(
                new Measure("foo", 1, new Tag("a", "b")));
    }

    @Test
    public void testTimer() throws Exception {
        Timer timer = mock(Timer.class);
        when(timer.getCount()).thenReturn(1L);
        when(timer.getMeanRate()).thenReturn(2d);
        when(timer.getOneMinuteRate()).thenReturn(3d);
        when(timer.getFiveMinuteRate()).thenReturn(4d);
        when(timer.getFifteenMinuteRate()).thenReturn(5d);
        Snapshot snapshot = mock(Snapshot.class);
        when(timer.getSnapshot()).thenReturn(snapshot);
        when(snapshot.size()).thenReturn(1);
        when(snapshot.getMean()).thenReturn(6d);
        when(snapshot.getMin()).thenReturn(7L);
        when(snapshot.getMax()).thenReturn(8L);
        when(snapshot.getMedian()).thenReturn(9d);
        when(snapshot.get75thPercentile()).thenReturn(10d);
        when(snapshot.get95thPercentile()).thenReturn(11d);
        when(snapshot.get98thPercentile()).thenReturn(12d);
        when(snapshot.get99thPercentile()).thenReturn(13d);
        when(snapshot.get999thPercentile()).thenReturn(14d);
        registry.register("foo", timer);
        AppopticsReporter reporter = new AppopticsReporter(atts);
        report(reporter);
        HashSet<IMeasure> measures = new HashSet<IMeasure>(captor.getValue().getMeasures());
        assertThat(measures).containsOnly(
                new Measure("foo", snapshot.getMean() * timer.getCount(), timer.getCount(), snapshot.getMin(), snapshot.getMax()),
                new Measure("foo.count", timer.getCount()),
                new Measure("foo.median", snapshot.getMedian()),
                new Measure("foo.75th", snapshot.get75thPercentile()),
                new Measure("foo.95th", snapshot.get95thPercentile()),
                new Measure("foo.98th", snapshot.get98thPercentile()),
                new Measure("foo.99th", snapshot.get99thPercentile()),
                new Measure("foo.999th", snapshot.get999thPercentile()),
                new Measure("foo.meanRate", timer.getMeanRate()),
                new Measure("foo.1MinuteRate", timer.getOneMinuteRate()),
                new Measure("foo.5MinuteRate", timer.getFiveMinuteRate()),
                new Measure("foo.15MinuteRate", timer.getFifteenMinuteRate()));
    }

    @Test
    public void testTaggedTimer() throws Exception {
        Timer timer = mock(Timer.class);
        when(timer.getCount()).thenReturn(1L);
        when(timer.getMeanRate()).thenReturn(2d);
        when(timer.getOneMinuteRate()).thenReturn(3d);
        when(timer.getFiveMinuteRate()).thenReturn(4d);
        when(timer.getFifteenMinuteRate()).thenReturn(5d);
        Snapshot snapshot = mock(Snapshot.class);
        when(timer.getSnapshot()).thenReturn(snapshot);
        when(snapshot.size()).thenReturn(1);
        when(snapshot.getMean()).thenReturn(6d);
        when(snapshot.getMin()).thenReturn(7L);
        when(snapshot.getMax()).thenReturn(8L);
        when(snapshot.getMedian()).thenReturn(9d);
        when(snapshot.get75thPercentile()).thenReturn(10d);
        when(snapshot.get95thPercentile()).thenReturn(11d);
        when(snapshot.get98thPercentile()).thenReturn(12d);
        when(snapshot.get99thPercentile()).thenReturn(13d);
        when(snapshot.get999thPercentile()).thenReturn(14d);
        Appoptics.metric(registry, "foo").tag("a", "z").timer(timer);
        AppopticsReporter reporter = new AppopticsReporter(atts);
        report(reporter);
        HashSet<IMeasure> measures = new HashSet<IMeasure>(captor.getValue().getMeasures());
        assertThat(measures).containsOnly(
                new Measure("foo", snapshot.getMean() * timer.getCount(), timer.getCount(), snapshot.getMin(), snapshot.getMax(), new Tag("a", "z")),
                new Measure("foo.count", timer.getCount(), new Tag("a", "z")),
                new Measure("foo.median", snapshot.getMedian(), new Tag("a", "z")),
                new Measure("foo.75th", snapshot.get75thPercentile(), new Tag("a", "z")),
                new Measure("foo.95th", snapshot.get95thPercentile(), new Tag("a", "z")),
                new Measure("foo.98th", snapshot.get98thPercentile(), new Tag("a", "z")),
                new Measure("foo.99th", snapshot.get99thPercentile(), new Tag("a", "z")),
                new Measure("foo.999th", snapshot.get999thPercentile(), new Tag("a", "z")),
                new Measure("foo.meanRate", timer.getMeanRate(), new Tag("a", "z")),
                new Measure("foo.1MinuteRate", timer.getOneMinuteRate(), new Tag("a", "z")),
                new Measure("foo.5MinuteRate", timer.getFiveMinuteRate(), new Tag("a", "z")),
                new Measure("foo.15MinuteRate", timer.getFifteenMinuteRate(), new Tag("a", "z")));
    }

    @Test
    public void testMeter() throws Exception {
        Meter meter = mock(Meter.class);
        when(meter.getCount()).thenReturn(1L);
        when(meter.getMeanRate()).thenReturn(2d);
        when(meter.getOneMinuteRate()).thenReturn(3d);
        when(meter.getFiveMinuteRate()).thenReturn(4d);
        when(meter.getFifteenMinuteRate()).thenReturn(5d);
        registry.register("foo", meter);
        AppopticsReporter reporter = new AppopticsReporter(atts);
        report(reporter);
        HashSet<IMeasure> measures = new HashSet<IMeasure>(captor.getValue().getMeasures());
        assertThat(measures).containsOnly(
                new Measure("foo.count", 1),
                new Measure("foo.meanRate", meter.getMeanRate()),
                new Measure("foo.1MinuteRate", meter.getOneMinuteRate()),
                new Measure("foo.5MinuteRate", meter.getFiveMinuteRate()),
                new Measure("foo.15MinuteRate", meter.getFifteenMinuteRate()));
    }

    @Test
    public void testTaggedMeter() throws Exception {
        Meter meter = mock(Meter.class);
        when(meter.getCount()).thenReturn(1L);
        when(meter.getMeanRate()).thenReturn(2d);
        when(meter.getOneMinuteRate()).thenReturn(3d);
        when(meter.getFiveMinuteRate()).thenReturn(4d);
        when(meter.getFifteenMinuteRate()).thenReturn(5d);
        Appoptics.metric(registry, "foo").tag("a", "z").meter(meter);
        AppopticsReporter reporter = new AppopticsReporter(atts);
        report(reporter);
        HashSet<IMeasure> measures = new HashSet<IMeasure>(captor.getValue().getMeasures());
        assertThat(measures).containsOnly(
                new Measure("foo.count", 1, new Tag("a", "z")),
                new Measure("foo.meanRate", meter.getMeanRate(), new Tag("a", "z")),
                new Measure("foo.1MinuteRate", meter.getOneMinuteRate(), new Tag("a", "z")),
                new Measure("foo.5MinuteRate", meter.getFiveMinuteRate(), new Tag("a", "z")),
                new Measure("foo.15MinuteRate", meter.getFifteenMinuteRate(), new Tag("a", "z")));
    }

    @Test
    public void testHisto() throws Exception {
        Histogram histo = new Histogram(new UniformReservoir());
        histo.update(42);
        registry.register("foo", histo);
        AppopticsReporter reporter = new AppopticsReporter(atts);
        report(reporter);
        HashSet<IMeasure> measures = new HashSet<IMeasure>(captor.getValue().getMeasures());
        assertThat(measures).containsOnly(
                new Measure("foo", 42, 1, 42, 42),
                new Measure("foo.count", 1),
                new Measure("foo.median", 42),
                new Measure("foo.75th", 42),
                new Measure("foo.95th", 42),
                new Measure("foo.98th", 42),
                new Measure("foo.99th", 42),
                new Measure("foo.999th", 42));
    }

    @Test
    public void testTaggedHisto() throws Exception {
        Histogram histo = new Histogram(new UniformReservoir());
        histo.update(42);
        Appoptics.metric(registry, "foo").tag("a", "z").histogram(histo);
        AppopticsReporter reporter = new AppopticsReporter(atts);
        report(reporter);
        HashSet<IMeasure> measures = new HashSet<IMeasure>(captor.getValue().getMeasures());
        assertThat(measures).containsOnly(
                new Measure("foo", 42, 1, 42, 42, new Tag("a", "z")),
                new Measure("foo.count", 1, new Tag("a", "z")),
                new Measure("foo.median", 42, new Tag("a", "z")),
                new Measure("foo.75th", 42, new Tag("a", "z")),
                new Measure("foo.95th", 42, new Tag("a", "z")),
                new Measure("foo.98th", 42, new Tag("a", "z")),
                new Measure("foo.99th", 42, new Tag("a", "z")),
                new Measure("foo.999th", 42, new Tag("a", "z")));
    }
}
