package com.librato.metrics.reporter;

import com.appoptics.metrics.reporter.*;
import com.codahale.metrics.*;
import com.appoptics.metrics.client.Duration;
import com.appoptics.metrics.client.Tag;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This provides a (largely) backwards compatible metrics interface to reduce
 * the work required to upgrade. This is deprecated and will be removed in a
 * future version, so should not be used in new code.
 */
@Deprecated
public class Librato {
    private static final NameCache nameCache = new NameCache(5000);
    private final MetricRegistry registry;
    private final String name;
    private List<Tag> tags = Collections.emptyList();
    private boolean overrideTags;
    private Supplier<Reservoir> reservoir = Appoptics.defaultReservoir.get();

    public static Librato metric(String name) {
        MetricRegistry registry = Appoptics.defaultRegistry.get();
        return metric(registry, name);
    }

    public static Librato metric(MetricRegistry registry, String name) {
        return new Librato(registry, name);
    }

    public Librato(MetricRegistry registry, String name) {
        this.registry = registry;
        this.name = name;
    }

    public Librato reservoir(Supplier<Reservoir> reservoir) {
        this.reservoir = reservoir;
        return this;
    }

    public Librato window() {
        this.reservoir = new Supplier<Reservoir>() {
            @Override
            public Reservoir get() {
                Duration window = Appoptics.defaultWindow.get();
                return new SlidingTimeWindowArrayReservoir(window.duration, window.timeUnit);
            }
        };
        return this;
    }

    public Librato window(final long window, final TimeUnit unit) {
        this.reservoir = new Supplier<Reservoir>() {
            @Override
            public Reservoir get() {
                return new SlidingTimeWindowArrayReservoir(window, unit);
            }
        };
        return this;
    }

    public Librato source(Object source) {
        addTag(new Tag("source", source.toString()));
        return this;
    }

    public Librato tag(String name, Object value) {
        addTag(new Tag(name, value.toString()));
        return this;
    }

    public Librato tags(Tag... tags) {
        for (Tag tag : tags) {
            addTag(tag);
        }
        return this;
    }

    public Librato tags(List<Tag> tags) {
        for (Tag tag : tags) {
            addTag(tag);
        }
        return this;
    }

    public Librato doNotInheritTags() {
        this.overrideTags = true;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> Gauge<T> gauge(final Gauge<T> gauge) {
        return register(Gauge.class, new Supplier<Gauge>() {
            @Override
            public Gauge get() {
                return gauge;
            }
        });
    }

    public Counter counter() {
        return counter(null);
    }

    public Counter counter(final Counter counter) {
        return register(Counter.class, new Supplier<Counter>() {
            @Override
            public Counter get() {
                if (counter != null) {
                    return counter;
                }
                return new Counter();
            }
        });
    }

    public Histogram histogram() {
        return histogram(null);
    }

    public Histogram histogram(final Histogram histogram) {
        return register(Histogram.class, new Supplier<Histogram>() {
            @Override
            public Histogram get() {
                if (histogram != null) {
                    return histogram;
                }
                return new Histogram(reservoir.get());
            }
        });
    }

    public Meter meter() {
        return meter(null);
    }

    public Meter meter(final Meter meter) {
        return register(Meter.class, new Supplier<Meter>() {
            @Override
            public Meter get() {
                if (meter != null) {
                    return meter;
                }
                return new Meter();
            }
        });
    }

    public Timer timer() {
        return timer(null);
    }

    public Timer timer(final Timer timer) {
        return register(Timer.class, new Supplier<Timer>() {
            @Override
            public Timer get() {
                if (timer != null) {
                    return timer;
                }
                return new Timer(reservoir.get());
            }
        });
    }

    public boolean remove() {
        Signal signal = createSignal();
        if (signal == null) {
            return registry.remove(name);
        }
        return registry.remove(encodeName(signal));
    }

    private void addTag(Tag tag) {
        if (this.tags.isEmpty()) {
            this.tags = new LinkedList<Tag>();
        }
        this.tags.add(tag);
    }

    private <T extends Metric> T register(Class<T> klass, Supplier<T> metric) {
        Signal signal = createSignal();
        if (signal == null) {
            return register(registry, name, metric, klass);
        }
        String encodedName = encodeName(signal);
        return register(registry, encodedName, metric, klass);
    }

    private <T extends Metric> T register(MetricRegistry registry,
                                          String name,
                                          Supplier<T> metric,
                                          Class<T> klass) {
        Metric found = registry.getMetrics().get(name);
        if (found != null) {
            return verifyFound(found, klass);
        }
        try {
            return registry.register(name, metric.get());
        } catch (IllegalArgumentException e) {
            found = registry.getMetrics().get(name);
            if (found == null) {
                throw e;
            }
            return verifyFound(found, klass);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Metric> T verifyFound(Metric found, Class<T> klass) {
        if (!klass.isAssignableFrom(found.getClass())) {
            throw new RuntimeException("A metric with " + found.getClass() + " already exists for this name");
        }
        return (T)found;
    }

    private Signal createSignal() {
        if (tags.isEmpty()) {
            return null;
        }
        return new Signal(name, tags, overrideTags);
    }


    private String encodeName(final Signal signal) {
        return nameCache.get(signal, new Supplier<String>() {
            @Override
            public String get() {
                return Json.serialize(signal);
            }
        });
    }
}
