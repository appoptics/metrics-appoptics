package com.appoptics.metrics.reporter;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.appoptics.metrics.client.Duration;
import com.appoptics.metrics.client.IPoster;
import com.appoptics.metrics.client.Tag;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ReporterAttributes {
    String url = "https://api.appoptics.com";
    String reporterName = "appoptics";
    MetricFilter metricFilter = MetricFilter.ALL;
    TimeUnit rateUnit = TimeUnit.SECONDS;
    TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    MetricRegistry registry = new MetricRegistry();
    String token;
    String prefix;
    String prefixDelimiter = ".";
    MetricExpansionConfig expansionConfig = MetricExpansionConfig.ALL;
    boolean deleteIdleStats = true;
    boolean omitComplexGauges;
    Duration readTimeout;
    Duration connectTimeout;
    List<Tag> tags = new LinkedList<Tag>();
    IAppopticsClientFactory appopticsClientFactory = new DefaultAppopticsClientFactory();
    RateConverter rateConverter;
    DurationConverter durationConverter;
    IPoster poster;
}
