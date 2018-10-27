## Appoptics Plugin for the Metrics Library

The `AppopticsReporter` class runs in the background, publishing metrics from
[dropwizard/metrics](http://metrics.dropwizard.io/) to the
 [AppOptics API](https://www.appoptics.com) at the specified interval.

Find the latest [version here](https://search.maven.org/search?q=g:com.appoptics.metrics%20AND%20a:metrics-appoptics&core=gav).

	<dependency>
	  <groupId>com.appoptics.metrics</groupId>
	  <artifactId>metrics-appoptics</artifactId>
	  <version>1.0.0</version>
	</dependency>

## Usage

Start the reporter in your application bootstrap:

    MetricRegistry registry = environment.metrics(); // if you're not using dropwizard, use your own registry
    Appoptics.reporter(registry, "<AppOptics API Token>")
        .addTag("tier", "web")
        .addTag("environment", "staging")
        .start(10, TimeUnit.SECONDS);

The tags you add in this way will be included on every measure. If you wish to
supply custom tags at runtime you can use the Appoptics helper:

    Appoptics.metric("logins").tag("userId", userId).meter().mark()


## Fluent Helper

The Appoptics fluent helper provides a number of ways to make it easy to
interface with Dropwizard Metrics.  You do not need to use this class but if 
you want to specify custom tags, it will be easier. Some examples:

    Appoptics.metric(registry, "logins").tag("uid", uid).meter().mark()
    Appoptics.metric(registry, "kafka-read-latencies").tag("broker", broker).histogram().update(latency)
    Appoptics.metric(registry, "temperature").tag("type", "celcius").gauge(() -> 42))
    Appoptics.metric(registry, "jobs-processed").tag("service", "ebs").meter().mark()
    Appoptics.metric(registry, "just-these-tags").tag('"foo", "bar").doNotInheritTags().timer.update(time)

When you start the Appoptics reporter as described earlier, that will set the 
registry used to start it as the default registry in the fluent helper.  That 
lets you simply use the shorter form:

    Appoptics.metric("logins").tag("uid", uid).meter().mark()

## AppOptics Metrics Used

This library will output a few different kinds of metrics to AppOptics:

* Gauges: a measurement at a point in time
* DeltaGauge: a gauge that submits the delta between the current value of the 
  gauge and the previous value. Note that the reporter will omit the first value
  for a DeltaGauge because it knows no previous value at that time.
* ComplexGauge: includes sum, count, min, max, and average measurements. See 
  [the API documentation](https://docs.appoptics.com/api/#create-a-measurement)
  for more information on extended gauge parameters.

## Translation to AppOptics Metrics

This section describes how each of the Coda metrics translate into AppOptics metrics.

### Coda Gauges

Given a Coda Gauge with name `foo`, the following values are reported:

* Gauge: name=foo

The value reported for the Gauge is the current value of the Coda Gauge at flush time.

### Coda Counters

Given a Coda Counter with name `foo`, the following values are reported:

* Gauge: name=foo

The value reported for the Gauge is the current value of the Coda Counter at flush time.

### Coda Histograms

Given a Coda Histogram with name `foo`, the following values are reported:

* ComplexGauge: name=foo
* Gauge: name=foo.median
* Gauge: name=foo.75th
* Gauge: name=foo.95th
* Gauge: name=foo.98th
* Gauge: name=foo.99th
* Gauge: name=foo.999th
* DeltaGauge: name=foo.count (represents the number of values the Coda Histogram has recorded)

_Note that Coda Histogram percentiles are determined using configurable 
[Reservoir Sampling](https://dropwizard.github.io/metrics/3.1.0/manual/core/#histograms). 
Histograms by default use a non-biased uniform reservoir._

### Coda Meters

Given a Coda Meter with name `foo`, the following values are reported:

* DeltaGauge: name=foo.count (represents the number of values the Coda Meter has recorded)
* Gauge: name=foo.meanRate
* Gauge: name=foo.1MinuteRate
* Gauge: name=foo.5MinuteRate
* Gauge: name=foo.15MinuteRate

### Coda Timers

Coda Timers compose a Coda Meter as well as a Coda Histogram, so the values 
reported to AppOptics are the union of the values reported for these two metric types.

Given a Coda Timer with name `foo`, the following values are reported:

* ComplexGauge: name=foo
* Gauge: name=foo.median
* Gauge: name=foo.75th
* Gauge: name=foo.95th
* Gauge: name=foo.98th
* Gauge: name=foo.99th
* Gauge: name=foo.999th
* DeltaGauge: name=foo.count (represents the number of values the Coda Timer has recorded)
* Gauge: name=foo.meanRate
* Gauge: name=foo.1MinuteRate
* Gauge: name=foo.5MinuteRate
* Gauge: name=foo.15MinuteRate

_Note that Coda Timer percentiles are determined using configurable 
[Reservoir Sampling](https://dropwizard.github.io/metrics/3.1.0/manual/core/#histograms). 
Coda Timers by default use an exponentially decaying reservoir to prioritize newer data._

## Reducing The Volume Of Metrics Reported

### Eliding Certain Metrics

While this library aims to accurately report all of the data that Coda Metrics 
provides, it can become somewhat verbose. One can reduce the number of metrics 
reported for Coda Timers, Coda Meters, and Coda Histograms when configuring the 
reporter. The percentiles, rates, and count for these metrics can be whitelisted 
(they are all on by default). In order to do this, supply a 
`AppopticsReporter.MetricExpansionConfig` to the builder:

    Appoptics.reporter(registry, <token>)
        .setExpansionConfig(
            new MetricExpansionConfig(
                EnumSet.of(
                    AppopticsReporter.ExpandedMetric.PCT_95,
                    AppopticsReporter.ExpandedMetric.RATE_1_MINUTE)))

In this configuration, the reporter will only report the 95th percentile and 1 minute rate for these metrics. Note that the `ComplexGauge`s will still be reported.

### Eliding Complex Gauges

Timers and Histograms end up generating a complex gauge along with any other expanded metrics that are configured to be sent to Appoptics. If you wish to exclude these complex gauges, one may enable `omitComplexGauges` in the AppopticsReporter.

    Appoptics.reporter(registry, <token>)
      .setOmitComplexGauges(true)

Note that in addition to the mean, complex gauges also include the minimum and maximum dimensions, so if you choose to enable this option, you will no longer have access to those summaries for those metrics.

### Idle Stat Detection

The _idle stats_ feature detects when certain types of metrics (Meters, Histograms, and Timers) stop getting updated by the application. When this happens, `metrics-appoptics` will stop reporting these streams to AppOptics until they are updated again. Since AppOptics does not charge for metrics which are not submitted to the API, this can lower your cost, especially for metrics that report infrequently.

This is enabled by default, but should you wish to disable this feature, you can do so when setting up the AppopticsReporter:

    Appoptics.reporter(registry, <token>)
    	.setDeleteIdleStats(false)

## Using Dropwizard?

The [dropwizard-appoptics](https://github.com/appoptics/dropwizard-appoptics) project allows you to send Metrics from within your Dropwizard application to AppOptics by adding a section to your config file.
