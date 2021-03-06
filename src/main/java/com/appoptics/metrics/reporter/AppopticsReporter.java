package com.appoptics.metrics.reporter;

import com.codahale.metrics.*;
import com.appoptics.metrics.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import static com.appoptics.metrics.reporter.ExpandedMetric.*;

/**
 * The main class in this library
 */
public class AppopticsReporter extends ScheduledReporter implements RateConverter, DurationConverter {
    private static final Logger log = LoggerFactory.getLogger(AppopticsReporter.class);
    private static MetricRegistry registry;
    private final AppopticsClient client;
    private final DeltaTracker deltaTracker;
    private final String prefix;
    private final String prefixDelimiter;
    private final MetricExpansionConfig expansionConfig;
    private final boolean deleteIdleStats;
    private final boolean omitComplexGauges;
    private final List<Tag> tags;
    private final RateConverter rateConverter;
    private final DurationConverter durationConverter;
    private volatile Integer defaultPeriod;

    public static ReporterBuilder builder(MetricRegistry registry,
                                          String token) {
        return new ReporterBuilder(registry, token);
    }

    /*
     * Constructor. Should be called from the builder.
     */
    AppopticsReporter(ReporterAttributes atts) {
        super(atts.registry,
                atts.reporterName,
                atts.metricFilter,
                atts.rateUnit,
                atts.durationUnit);
        Appoptics.defaultRegistry.set(atts.registry);
        this.client = atts.appopticsClientFactory.build(atts);
        this.deltaTracker = new DeltaTracker(new DeltaMetricSupplier(atts.registry));
        this.prefix = checkPrefix(atts.prefix);
        this.prefixDelimiter = atts.prefixDelimiter;
        this.expansionConfig = atts.expansionConfig;
        this.deleteIdleStats = atts.deleteIdleStats;
        this.omitComplexGauges = atts.omitComplexGauges;
        this.tags = atts.tags;
        this.rateConverter = atts.rateConverter != null ? atts.rateConverter : this;
        this.durationConverter = atts.durationConverter != null ? atts.durationConverter : this;
    }

    @Override
    public void start(long period, TimeUnit unit) {
        Appoptics.defaultWindow.set(new Duration(period, unit));
        defaultPeriod = (int) (unit.toSeconds(period));
        super.start(period, unit);
    }

    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {
        long epoch = System.currentTimeMillis() / 1000;
        Measures measures = new Measures(Collections.<Tag>emptyList(), epoch, defaultPeriod);
        addGauges(measures, gauges);
        addCounters(measures, counters);
        addHistograms(measures, histograms);
        addMeters(measures, meters);
        addTimers(measures, timers);
        try {
            PostMeasuresResult postResults = client.postMeasures(measures);
            for (PostResult result : postResults.results) {
                if (result.isError()) {
                    handlePostFailure(result);
                }
            }
        } catch (Exception e) {
            log.error("Failure to post to Librato", e);
        }
    }

    private void handlePostFailure(PostResult result) {
        Exception exception = result.exception;
        if (exception != null) {
            handlePostFailure(exception);
            return;
        }
        log.error("Failure to post to Librato: " + result.toString());
    }

    private void handlePostFailure(Exception e) {
        Throwable cause = getCause(e);
        if (cause instanceof SocketTimeoutException) {
            log.warn("Could not connect to Librato", cause);
            return;
        }
        log.warn("Failure to post to Librato", e);
    }

    private Throwable getCause(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private void addGauges(Measures measures, SortedMap<String, Gauge> gauges) {
        for (String metricName : gauges.keySet()) {
            Gauge gauge = gauges.get(metricName);
            Number number = Numbers.getNumberFrom(gauge.getValue());
            if (number != null) {
                addAppopticsGauge(measures, convertToSignal(metricName), number.doubleValue());
            }
        }
    }

    private void addCounters(Measures measures, SortedMap<String, Counter> counters) {
        for (String metricName : counters.keySet()) {
            Counter counter = counters.get(metricName);
            long count = counter.getCount();
            addAppopticsGauge(measures, convertToSignal(metricName), count);
        }
    }

    private void addHistograms(Measures measures, SortedMap<String, Histogram> histograms) {
        for (String metricName : histograms.keySet()) {
            Histogram histogram = histograms.get(metricName);
            if (skipMetric(metricName, histogram)) {
                continue;
            }
            Long countDelta = deltaTracker.getDelta(metricName, histogram.getCount());
            maybeAdd(measures, COUNT, metricName, countDelta);
            final boolean convertDurations = false;
            addSampling(measures, metricName, histogram, convertDurations);
        }
    }

    private void addMeters(Measures measures, SortedMap<String, Meter> meters) {
        for (String metricName : meters.keySet()) {
            Meter meter = meters.get(metricName);
            if (skipMetric(metricName, meter)) {
                continue;
            }
            addMeter(measures, metricName, meter);
        }
    }

    private void addMeter(Measures measures, String metricName, Metered meter) {
        Long countDelta = deltaTracker.getDelta(metricName, meter.getCount());
        maybeAdd(measures, COUNT, metricName, countDelta);
        maybeAdd(measures, RATE_MEAN, metricName, doConvertRate(meter.getMeanRate()));
        maybeAdd(measures, RATE_1_MINUTE, metricName, doConvertRate(meter.getOneMinuteRate()));
        maybeAdd(measures, RATE_5_MINUTE, metricName, doConvertRate(meter.getFiveMinuteRate()));
        maybeAdd(measures, RATE_15_MINUTE, metricName, doConvertRate(meter.getFifteenMinuteRate()));
    }

    private void addTimers(Measures measures, SortedMap<String, Timer> timers) {
        for (String metricName : timers.keySet()) {
            Timer timer = timers.get(metricName);
            if (skipMetric(metricName, timer)) {
                continue;
            }
            addMeter(measures, metricName, timer);
            final boolean convertDurations = true;
            addSampling(measures, metricName, timer, convertDurations);
        }
    }

    private void addSampling(Measures measures, String name, Sampling sampling, boolean convert) {
        final Snapshot snapshot = sampling.getSnapshot();
        maybeAdd(measures, MEDIAN, name, doConvertDuration(snapshot.getMedian(), convert));
        maybeAdd(measures, PCT_75, name, doConvertDuration(snapshot.get75thPercentile(), convert));
        maybeAdd(measures, PCT_95, name, doConvertDuration(snapshot.get95thPercentile(), convert));
        maybeAdd(measures, PCT_98, name, doConvertDuration(snapshot.get98thPercentile(), convert));
        maybeAdd(measures, PCT_99, name, doConvertDuration(snapshot.get99thPercentile(), convert));
        maybeAdd(measures, PCT_999, name, doConvertDuration(snapshot.get999thPercentile(), convert));
        if (!omitComplexGauges) {
            final double sum = snapshot.size() * snapshot.getMean();
            final long count = (long) snapshot.size();
            if (count > 0) {
                try {
                    addAppopticsGauge(measures, convertToSignal(name),
                            doConvertDuration(sum, convert),
                            count,
                            doConvertDuration(snapshot.getMin(), convert),
                            doConvertDuration(snapshot.getMax(), convert));
                } catch (IllegalArgumentException e) {
                    log.warn("Could not create gauge", e);
                }
            }
        }
    }

    private void addAppopticsGauge(Measures measures, Signal signal, double sum, long count, double min, double max) {
        Measure gauge = new Measure(signal.name, sum, count, min, max);
        addAppopticsGauge(measures, signal, gauge);
    }

    private void addAppopticsGauge(Measures measures, Signal signal, double value) {
        Measure gauge = new Measure(signal.name, value);
        addAppopticsGauge(measures, signal, gauge);
    }

    private void addAppopticsGauge(Measures measures, Signal signal, Measure gauge) {
        for (Tag tag : signal.tags) {
            gauge.addTag(tag);
        }
        if (!signal.overrideTags) {
            for (Tag tag : tags) {
                gauge.addTag(tag);
            }
        }
        measures.add(gauge);
    }

    private String addPrefix(String metricName) {
        if (prefix == null || prefix.length() == 0) {
            return metricName;
        }
        return prefix + prefixDelimiter + metricName;
    }

    private String checkPrefix(String prefix) {
        if ("".equals(prefix)) {
            throw new IllegalArgumentException("Prefix may either be null or a non-empty string");
        }
        return prefix;
    }

    private void maybeAdd(Measures measures, ExpandedMetric expandedMetric, String name, Number reading) {
        if (expansionConfig.isSet(expandedMetric)) {
            if (!Numbers.isANumber(reading)) {
                return;
            }
            Signal signal = convertToSignal(name, expandedMetric);
            addAppopticsGauge(measures, signal, reading.doubleValue());
        }
    }

    private Signal convertToSignal(String registryName) {
        return convertToSignal(registryName, null);
    }

    private Signal convertToSignal(final String registryName, ExpandedMetric expandedMetric) {
        Signal signal = Signal.decode(registryName);
        if (expandedMetric != null) {
            signal.name = expandedMetric.buildMetricName(signal.name);
        }
        signal.name = addPrefix(signal.name);
        return signal;
    }

    private boolean skipMetric(String name, Counting counting) {
        return deleteIdleStats() && deltaTracker.peekDelta(name, counting.getCount()) == 0;
    }

    private boolean deleteIdleStats() {
        return deleteIdleStats;
    }

    private double doConvertDuration(double duration, boolean convert) {
        return convert ? durationConverter.convertDuration(duration) : duration;
    }

    private double doConvertRate(double rate) {
        return rateConverter.convertRate(rate);
    }

    @Override
    public double convertRate(double rate) {
        return super.convertRate(rate);
    }

    @Override
    public double convertDuration(double duration) {
        return super.convertDuration(duration);
    }
}
