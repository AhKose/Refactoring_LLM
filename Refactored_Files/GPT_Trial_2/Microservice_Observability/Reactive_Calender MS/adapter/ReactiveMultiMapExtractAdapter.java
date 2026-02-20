package com.microservices.calendar.api.adapter;

import io.opentracing.propagation.TextMap;
import io.vertx.reactivex.core.MultiMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Raphael Abreu
 */
public class ReactiveMultiMapExtractAdapter extends AbstractTextMapExtractAdapter implements TextMap {

    private final MultiMap headers;

    public ReactiveMultiMapExtractAdapter(MultiMap headers) {
        this.headers = headers;
    }

    @Override
    protected Iterator<Map.Entry<String, String>> entriesIterator() {
        return headers.entries().iterator();
    }
}

