package com.microservices.calendar.api.adapter;

import io.opentracing.propagation.TextMap;
import io.vertx.core.MultiMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Pavol Loffay
 */
public class MultiMapExtractAdapter implements TextMap {

    private final MultiMap headers;

    public MultiMapExtractAdapter(MultiMap headers) {
        if (headers == null) {
            throw new NullPointerException("headers");
        }
        this.headers = headers;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return headers.entries().iterator();
    }

    @Override
    public void put(String key, String value) {
        throw new UnsupportedOperationException();
    }
}

