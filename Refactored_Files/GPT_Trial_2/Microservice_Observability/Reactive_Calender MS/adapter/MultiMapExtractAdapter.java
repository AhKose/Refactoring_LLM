package com.microservices.calendar.api.adapter;

import io.opentracing.propagation.TextMap;
import io.vertx.core.MultiMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Pavol Loffay
 */
public class MultiMapExtractAdapter extends AbstractTextMapExtractAdapter implements TextMap {

    private final MultiMap headers;

    public MultiMapExtractAdapter(MultiMap headers) {
        this.headers = headers;
    }

    @Override
    protected Iterator<Map.Entry<String, String>> entriesIterator() {
        return headers.entries().iterator();
    }
}

abstract class AbstractTextMapExtractAdapter implements TextMap {

    protected abstract Iterator<Map.Entry<String, String>> entriesIterator();

    @Override
    public final Iterator<Map.Entry<String, String>> iterator() {
        return entriesIterator();
    }

    @Override
    public final void put(String key, String value) {
        throw new UnsupportedOperationException();
    }
}

