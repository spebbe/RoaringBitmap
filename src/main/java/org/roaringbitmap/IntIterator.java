/*
 * Copyright 2013-2014 by Daniel Lemire, Owen Kaser and Samy Chambi
 * Licensed under the Apache License, Version 2.0.
 */
package org.roaringbitmap;

/**
 * A simple iterator over integer values
 */
public interface IntIterator {
    /**
     * @return whether there is another value
     */
    boolean hasNext();

    /**
     * @return next integer value
     */
    int next();

    /**
     * remove current value
     */
    void remove();
}
