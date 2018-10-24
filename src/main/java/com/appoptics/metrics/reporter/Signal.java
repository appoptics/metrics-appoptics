package com.appoptics.metrics.reporter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.appoptics.metrics.client.Tag;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Signal {
    public String name;
    public List<Tag> tags = Collections.emptyList();
    public boolean overrideTags;

    public static Signal decode(String data) {
        if (data == null || data.length() < 2 || data.charAt(0) != '{') {
            return new Signal(data);
        }
        return Json.decode(data, Signal.class);
    }

    public Signal(String name) {
        this.name = name;
    }

    public Signal(String name, String source) {
        this.name = name;
    }

    @JsonCreator
    public Signal(@JsonProperty("name") String name,
                  @JsonProperty("tags") List<Tag> tags,
                  @JsonProperty("overrideTags") boolean overrideTags) {
        this.name = name;
        if (tags != null) {
            this.tags = tags;
        }
        this.overrideTags = overrideTags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Signal)) return false;
        Signal signal = (Signal) o;
        return overrideTags == signal.overrideTags &&
                Objects.equals(name, signal.name) &&
                Objects.equals(tags, signal.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tags, overrideTags);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Signal{");
        sb.append("name='").append(name).append('\'');
        sb.append(", tags=").append(tags);
        sb.append(", overrideTags=").append(overrideTags);
        sb.append('}');
        return sb.toString();
    }
}
