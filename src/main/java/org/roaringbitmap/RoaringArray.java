/*
 * Copyright 2013-2014 by Daniel Lemire, Owen Kaser and Samy Chambi
 * Licensed under the Apache License, Version 2.0.
 */
package org.roaringbitmap;

import java.io.*;
import java.util.Arrays;

/**
 * Specialized array to stored the containers used by a RoaringBitmap.
 */
public final class RoaringArray implements Cloneable, Externalizable {
    protected RoaringArray() {
        this.array = new Element[initialCapacity];
    }

    protected void append(short key, Container value) {
        extendArray(1);
        this.array[this.size++] = new Element(key, value);
    }

    /**
     * Append copy of the one value from another array
     *
     * @param sa    other array
     * @param index index in the other array
     */
    protected void appendCopy(RoaringArray sa, int index) {
        extendArray(1);
        this.array[this.size++] = new Element(sa.array[index].key,
                sa.array[index].value.clone());
    }

    /**
     * Append copies of the values from another array
     *
     * @param sa            other array
     * @param startingIndex starting index in the other array
     * @param end endingIndex (exclusive) in the other array
     */
    protected void appendCopy(RoaringArray sa, int startingIndex, int end) {
        extendArray(end - startingIndex);
        for (int i = startingIndex; i < end; ++i) {
            this.array[this.size++] = new Element(sa.array[i].key,
                    sa.array[i].value.clone());
        }

    }

    /**
     * Append copies of the values from another array, from the start
     *
     * @param sourceArray The array to copy from
     * @param stoppingKey any equal or larger key in other array will terminate
     *                    copying
     */
    protected void appendCopiesUntil(RoaringArray sourceArray, short stoppingKey) {
        int stopKey = Util.toIntUnsigned(stoppingKey);
        for (int i = 0; i < sourceArray.size; ++i) {
            if (Util.toIntUnsigned(sourceArray.array[i].key) >= stopKey)
                break;
            extendArray(1);
            this.array[this.size++] = new Element(sourceArray.array[i].key,
                    sourceArray.array[i].value.clone());
        }
    }

    /**
     * Append copies of the values AFTER a specified key (may or may not be
     * present) to end.
     *
     * @param sa          other array
     * @param beforeStart given key is the largest key that we won't copy
     */
    protected void appendCopiesAfter(RoaringArray sa, short beforeStart) {
        int startLocation = sa.getIndex(beforeStart);
        if (startLocation >= 0)
            startLocation++;
        else
            startLocation = -startLocation - 1;
        extendArray(sa.size - startLocation);

        for (int i = startLocation; i < sa.size; ++i) {
            this.array[this.size++] = new Element(sa.array[i].key,
                    sa.array[i].value.clone());
        }
    }

    protected void clear() {
        this.array = null;
        this.size = 0;
    }

    @Override
    public RoaringArray clone() throws CloneNotSupportedException {
        RoaringArray sa;
        sa = (RoaringArray) super.clone();
        sa.array = Arrays.copyOf(this.array, this.size);
        for (int k = 0; k < this.size; ++k)
            sa.array[k] = sa.array[k].clone();
        sa.size = this.size;
        return sa;
    }

    protected boolean containsKey(short x) {
        return (binarySearch(0, size, x) >= 0);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RoaringArray) {
            RoaringArray srb = (RoaringArray) o;
            if (srb.size != this.size)
                return false;
            for (int i = 0; i < srb.size; ++i) {
                if (this.array[i].key != srb.array[i].key)
                    return false;
                if (!this.array[i].value
                        .equals(srb.array[i].value))
                    return false;
            }
            return true;
        }
        return false;
    }

    // make sure there is capacity for at least k more elements
    protected void extendArray(int k) {
        // size + 1 could overflow
        if (this.size + k >= this.array.length) {
            int newCapacity;
            if (this.array.length < 1024) {
                newCapacity = 2 * (this.size + k);
            } else {
                newCapacity = 5 * (this.size + k) / 4;
            }
            this.array = Arrays.copyOf(this.array, newCapacity);
        }
    }

    // involves a binary search
    protected Container getContainer(short x) {
        int i = this.binarySearch(0, size, x);
        if (i < 0)
            return null;
        return this.array[i].value;
    }

    protected Container getContainerAtIndex(int i) {
        return this.array[i].value;
    }

    // involves a binary search
    protected int getIndex(short x) {
        // before the binary search, we optimize for frequent cases
        if ((size == 0) || (array[size - 1].key == x))
            return size - 1;
        // no luck we have to go through the list
        return this.binarySearch(0, size, x);
    }

    protected short getKeyAtIndex(int i) {
        return this.array[i].key;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

    // insert a new key, it is assumed that it does not exist
    protected void insertNewKeyValueAt(int i, short key, Container value) {
        extendArray(1);
        System.arraycopy(array, i, array, i + 1, size - i);
        array[i] = new Element(key, value);
        size++;
    }

    protected void resize(int newLength) {
        for (int k = newLength; k < this.size; ++k) {
            this.array[k] = null;
        }
        this.size = newLength;
    }

    protected boolean remove(short key) {
        int i = binarySearch(0, size, key);
        if (i >= 0) { // if a new key
            removeAtIndex(i);
            return true;
        }
        return false;
    }

    protected void removeAtIndex(int i) {
        System.arraycopy(array, i + 1, array, i, size - i - 1);
        array[size - 1] = null;
        size--;
    }

    protected void setContainerAtIndex(int i, Container c) {
        this.array[i].value = c;
    }

    protected int size() {
        return this.size;
    }

    private int binarySearch(int begin, int end, short key) {
        int low = begin;
        int high = end - 1;
        int ikey = Util.toIntUnsigned(key);

        while (low <= high) {
            int middleIndex = (low + high) >>> 1;
            int middleValue = Util
                    .toIntUnsigned(array[middleIndex].key);

            if (middleValue < ikey)
                low = middleIndex + 1;
            else if (middleValue > ikey)
                high = middleIndex - 1;
            else
                return middleIndex;
        }
        return -(low + 1);
    }

    protected Element[] array = null;

    protected int size = 0;

    static final int initialCapacity = 4;

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        deserialize(in);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        serialize(out);
    }

    /**
     * Serialize.
     * <p/>
     * The current bitmap is not modified.
     *
     * @param out the DataOutput stream
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void serialize(DataOutput out) throws IOException {
        out.write(serialCookie & 0xFF);
        out.write((serialCookie >>> 8) & 0xFF);
        out.write((serialCookie >>> 16) & 0xFF);
        out.write((serialCookie >>> 24) & 0xFF);

        out.write(this.size & 0xFF);
        out.write((this.size >>> 8) & 0xFF);
        out.write((this.size >>> 16) & 0xFF);
        out.write((this.size >>> 24) & 0xFF);

        for (int k = 0; k < size; ++k) {
            out.write(this.array[k].key & 0xFF);
            out.write((this.array[k].key >>> 8) & 0xFF);
            out.write((this.array[k].value.getCardinality() - 1) & 0xFF);
            out.write(((this.array[k].value.getCardinality() - 1) >>> 8) & 0xFF);
        }
        for (int k = 0; k < size; ++k) {
            array[k].value.writeArray(out);
        }
    }

    /**
     * Report the number of bytes required for serialization.
     *
     * @return the size in bytes
     */
    public int serializedSizeInBytes() {
        int count = 4 + 4 + 4 * size;
        for (int k = 0; k < size; ++k) {
            count += array[k].value.getArraySizeInBytes();
        }
        return count;
    }

    /**
     * Deserialize.
     *
     * @param in the DataInput stream
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void deserialize(DataInput in) throws IOException {
        this.clear();
        final byte[] buffer4 = new byte[4];
        final byte[] buffer = new byte[2];
        // little endian
        in.readFully(buffer4);
        final int cookie = (buffer4[0] & 0xFF) | ((buffer4[1] & 0xFF) << 8)
                | ((buffer4[2] & 0xFF) << 16)
                | ((buffer4[3] & 0xFF) << 24);
        if (cookie != serialCookie)
            throw new IOException(
                    "I failed to find the right cookie.");

        in.readFully(buffer4);
        this.size = (buffer4[0] & 0xFF) | ((buffer4[1] & 0xFF) << 8)
                | ((buffer4[2] & 0xFF) << 16)
                | ((buffer4[3] & 0xFF) << 24);
        if ((this.array == null) || (this.array.length < this.size))
            this.array = new Element[this.size];
        final short keys[] = new short[this.size];
        final int cardinalities[] = new int[this.size];
        final boolean isBitmap[] = new boolean[this.size];
        for (int k = 0; k < this.size; ++k) {
            in.readFully(buffer);
            keys[k] = (short) (buffer[0] & 0xFF | ((buffer[1] & 0xFF) << 8));
            in.readFully(buffer);
            cardinalities[k] = 1 + (buffer[0] & 0xFF | ((buffer[1] & 0xFF) << 8));
            isBitmap[k] = cardinalities[k] > ArrayContainer.DEFAULT_MAX_SIZE;
        }
        for (int k = 0; k < this.size; ++k) {
            Container val;
            if (isBitmap[k]) {
                final long[] bitmapArray = new long[BitmapContainer.MAX_CAPACITY / 64];
                final byte[] buf = new byte[8];
                // little endian
                for (int l = 0; l < bitmapArray.length; ++l) {
                    in.readFully(buf);
                    bitmapArray[l] = (((long) buf[7] << 56)
                            + ((long) (buf[6] & 255) << 48)
                            + ((long) (buf[5] & 255) << 40)
                            + ((long) (buf[4] & 255) << 32)
                            + ((long) (buf[3] & 255) << 24)
                            + ((buf[2] & 255) << 16)
                            + ((buf[1] & 255) << 8)
                            + (buf[0] & 255));
                }
                val = new BitmapContainer(bitmapArray,
                        cardinalities[k]);
            } else {
                final short[] shortArray = new short[cardinalities[k]];
                for (int l = 0; l < shortArray.length; ++l) {
                    in.readFully(buffer);
                    shortArray[l] =
                            (short) (buffer[0] & 0xFF | ((buffer[1] & 0xFF) << 8));
                }
                val = new ArrayContainer(shortArray);
            }
            this.array[k] = new Element(keys[k], val);
        }
    }

    protected static final short serialCookie = 12345;

    private static final long serialVersionUID = 7L;

    protected static final class Element implements Cloneable {
        public Element(short key, Container value) {
            this.key = key;
            this.value = value;
        }

        public short key;

        public Container value = null;

        @Override
        public Element clone() {
            Element c;
            try {
                c = (Element) super.clone();
                c.key = this.key; // OFK: wouldn't Object's
                // bitwise copy do this?
                c.value = this.value.clone();
                return c;
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }
}
