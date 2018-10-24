package com.appoptics.metrics.reporter;

import com.appoptics.metrics.client.AppopticsClient;

public interface IAppopticsClientFactory {
    AppopticsClient build(ReporterAttributes atts);
}
