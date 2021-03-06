/*
 * Copyright 2013-2014 by Daniel Lemire, Owen Kaser and Samy Chambi
 * Licensed under the Apache License, Version 2.0.
 */
package org.roaringbitmap.buffer;

import org.roaringbitmap.IntIterator;

import java.io.*;
import java.util.Iterator;

/**
 * RoaringBitmap, a compressed alternative to the BitSet. It is similar to
 * org.roaringbitmap.RoaringBitmap, but it differs in that it can be
 * memory-mapped using a ByteBuffer.
 */
public final class RoaringBitmap extends ImmutableRoaringBitmap implements
        Cloneable, Serializable, Iterable<Integer>, Externalizable {

    private static final long serialVersionUID = 3L;

    /**
     * Bitwise AND (intersection) operation. The provided bitmaps are *not*
     * modified. This operation is thread-safe as long as the provided
     * bitmaps remain unchanged.
     *
     * @param x1 first bitmap
     * @param x2 other bitmap
     * @return result of the operation
     */
    public static RoaringBitmap and(final RoaringBitmap x1, final RoaringBitmap x2) {
        final RoaringBitmap answer = new RoaringBitmap();
        int pos1 = 0, pos2 = 0;
        final int length1 = x1.highLowContainer.size(), length2 = x2.highLowContainer
                .size();
                /*
                 * TODO: This could be optimized quite a bit when one bitmap is
                 * much smaller than the other one.
                 */
        main:
        if (pos1 < length1 && pos2 < length2) {
            short s1 = x1.highLowContainer.getKeyAtIndex(pos1);
            short s2 = x2.highLowContainer.getKeyAtIndex(pos2);
            do {
                if (s1 < s2) {
                    pos1++;
                    if (pos1 == length1)
                        break main;
                    s1 = x1.highLowContainer.getKeyAtIndex(pos1);
                } else if (s1 > s2) {
                    pos2++;
                    if (pos2 == length2)
                        break main;
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                } else {
                    final Container c = x1.highLowContainer.getContainerAtIndex(pos1).and(
                            x2.highLowContainer.getContainerAtIndex(pos2));
                    if (c.getCardinality() > 0)
                        answer.highLowContainer.append(s1, c);
                    pos1++;
                    pos2++;
                    if ((pos1 == length1) || (pos2 == length2))
                        break main;
                    s1 = x1.highLowContainer.getKeyAtIndex(pos1);
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                }
            } while (true);
        }
        return answer;
    }

    /**
     * Bitwise ANDNOT (difference) operation. The provided bitmaps are *not*
     * modified. This operation is thread-safe as long as the provided
     * bitmaps remain unchanged.
     *
     * @param x1 first bitmap
     * @param x2 other bitmap
     * @return result of the operation
     */
    public static RoaringBitmap andNot(final RoaringBitmap x1,
                                       final RoaringBitmap x2) {
        final RoaringBitmap answer = new RoaringBitmap();
        int pos1 = 0, pos2 = 0;
        final int length1 = x1.highLowContainer.size(), length2 = x2.highLowContainer
                .size();
        main:
        if (pos1 < length1 && pos2 < length2) {
            short s1 = x1.highLowContainer.getKeyAtIndex(pos1);
            short s2 = x2.highLowContainer.getKeyAtIndex(pos2);
            do {
                if (s1 < s2) {
                    answer.highLowContainer.appendCopy(x1.highLowContainer, pos1);
                    pos1++;
                    if (pos1 == length1)
                        break main;
                    s1 = x1.highLowContainer.getKeyAtIndex(pos1);
                } else if (s1 > s2) {
                    pos2++;
                    if (pos2 == length2) {
                        break main;
                    }
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                } else {
                    final Container c = x1.highLowContainer.getContainerAtIndex(pos1).andNot(
                            x2.highLowContainer.getContainerAtIndex(pos2));
                    if (c.getCardinality() > 0)
                        answer.highLowContainer.append(s1, c);
                    pos1++;
                    pos2++;
                    if ((pos1 == length1) || (pos2 == length2))
                        break main;
                    s1 = x1.highLowContainer.getKeyAtIndex(pos1);
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                }
            } while (true);
        }
        if (pos2 == length2) {
            answer.highLowContainer.appendCopy(x1.highLowContainer, pos1, length1);
        }
        return answer;
    }

    /**
     * Generate a bitmap with the specified values set to true. The provided
     * integers values don't have to be in sorted order, but it may be
     * preferable to sort them from a performance point of view.
     *
     * @param dat set values
     * @return a new bitmap
     */
    public static RoaringBitmap bitmapOf(final int... dat) {
        final RoaringBitmap ans = new RoaringBitmap();
        for (final int i : dat)
            ans.add(i);
        return ans;
    }

    /**
     * Complements the bits in the given range, from rangeStart (inclusive)
     * rangeEnd (exclusive). The given bitmap is unchanged.
     *
     * @param bm         bitmap being negated
     * @param rangeStart inclusive beginning of range
     * @param rangeEnd   exclusive ending of range
     * @return a new Bitmap
     */
    public static RoaringBitmap flip(RoaringBitmap bm,final int rangeStart, final int rangeEnd) {
        if (rangeStart >= rangeEnd) {
            return bm.clone();
        }

        RoaringBitmap answer = new RoaringBitmap();
        final short hbStart = Util.highbits(rangeStart);
        final short lbStart = Util.lowbits(rangeStart);
        final short hbLast = Util.highbits(rangeEnd - 1);
        final short lbLast = Util.lowbits(rangeEnd - 1);

        // copy the containers before the active area
        answer.highLowContainer.appendCopiesUntil(bm.highLowContainer, hbStart);

        final int max = Util.toIntUnsigned(Util.maxLowBit());
        for (short hb = hbStart; hb <= hbLast; ++hb) {
            final int containerStart = (hb == hbStart) ? Util.toIntUnsigned(lbStart) : 0;
            final int containerLast = (hb == hbLast) ? Util.toIntUnsigned(lbLast) : max;

            final int i = bm.highLowContainer.getIndex(hb);
            final int j = answer.highLowContainer.getIndex(hb);
            assert j < 0;

            if (i >= 0) {
                final Container c = bm.highLowContainer.getContainerAtIndex(i).not(containerStart, containerLast);
                if (c.getCardinality() > 0)
                    answer.highLowContainer.insertNewKeyValueAt(-j - 1, hb, c);

            } else { // *think* the range of ones must never be
                // empty.
                answer.highLowContainer.insertNewKeyValueAt(-j - 1, hb, Container.rangeOfOnes(
                                containerStart, containerLast)
                );
            }
        }
        // copy the containers after the active area.
        answer.highLowContainer.appendCopiesAfter(bm.highLowContainer, hbLast);

        return answer;
    }

    /**
     * Bitwise OR (union) operation. The provided bitmaps are *not*
     * modified. This operation is thread-safe as long as the provided
     * bitmaps remain unchanged.
     *
     * @param x1 first bitmap
     * @param x2 other bitmap
     * @return result of the operation
     */
    public static RoaringBitmap or(final RoaringBitmap x1,
                                   final RoaringBitmap x2) {
        final RoaringBitmap answer = new RoaringBitmap();
        int pos1 = 0, pos2 = 0;
        final int length1 = x1.highLowContainer.size(), length2 = x2.highLowContainer
                .size();
        main:
        if (pos1 < length1 && pos2 < length2) {
            short s1 = x1.highLowContainer.getKeyAtIndex(pos1);
            short s2 = x2.highLowContainer.getKeyAtIndex(pos2);

            while (true) {
                if (s1 < s2) {
                    answer.highLowContainer.appendCopy(x1.highLowContainer, pos1);
                    pos1++;
                    if (pos1 == length1) {
                        break main;
                    }
                    s1 = x1.highLowContainer.getKeyAtIndex(pos1);
                } else if (s1 > s2) {
                    answer.highLowContainer.appendCopy(x2.highLowContainer, pos2);
                    pos2++;
                    if (pos2 == length2) {
                        break main;
                    }
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                } else {
                    answer.highLowContainer.append(s1,
                            x1.highLowContainer.getContainerAtIndex(pos1)
                                    .or(x2.highLowContainer.getContainerAtIndex(pos2))
                    );
                    pos1++;
                    pos2++;
                    if ((pos1 == length1) || (pos2 == length2)) {
                        break main;
                    }
                    s1 = x1.highLowContainer.getKeyAtIndex(pos1);
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                }
            }
        }
        if (pos1 == length1) {
            answer.highLowContainer.appendCopy(x2.highLowContainer, pos2, length2);
        } else if (pos2 == length2) {
            answer.highLowContainer.appendCopy(x1.highLowContainer, pos1, length1);
        }
        return answer;
    }


    /**
     * Bitwise XOR (symmetric difference) operation. The provided bitmaps
     * are *not* modified. This operation is thread-safe as long as the
     * provided bitmaps remain unchanged.
     *
     * @param x1 first bitmap
     * @param x2 other bitmap
     * @return result of the operation
     */
    public static RoaringBitmap xor(final RoaringBitmap x1,
                                    final RoaringBitmap x2) {
        final RoaringBitmap answer = new RoaringBitmap();
        int pos1 = 0, pos2 = 0;
        final int length1 = x1.highLowContainer.size(), length2 = x2.highLowContainer
                .size();

        main:
        if (pos1 < length1 && pos2 < length2) {
            short s1 = x1.highLowContainer.getKeyAtIndex(pos1);
            short s2 = x2.highLowContainer.getKeyAtIndex(pos2);

            while (true) {
                if (s1 < s2) {
                    answer.highLowContainer.appendCopy(x1.highLowContainer, pos1);
                    pos1++;
                    if (pos1 == length1) {
                        break main;
                    }
                    s1 = x1.highLowContainer.getKeyAtIndex(pos1);
                } else if (s1 > s2) {
                    answer.highLowContainer.appendCopy(x2.highLowContainer, pos2);
                    pos2++;
                    if (pos2 == length2) {
                        break main;
                    }
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                } else {
                    final Container c = x1.highLowContainer.getContainerAtIndex(pos1).xor(
                            x2.highLowContainer.getContainerAtIndex(pos2));
                    if (c.getCardinality() > 0)
                        answer.highLowContainer.append(s1, c);
                    pos1++;
                    pos2++;
                    if ((pos1 == length1) || (pos2 == length2)) {
                        break main;
                    }
                    s1 = x1.highLowContainer.getKeyAtIndex(pos1);
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                }
            }
        }
        if (pos1 == length1) {
            answer.highLowContainer.appendCopy(x2.highLowContainer, pos2, length2);
        } else if (pos2 == length2) {
            answer.highLowContainer.appendCopy(x1.highLowContainer, pos1, length1);
        }

        return answer;
    }


    /**
     * Create an empty bitmap
     */
    public RoaringBitmap() {
        highLowContainer = new RoaringArray();
    }

    /**
     * set the value to "true", whether it already appears or not.
     *
     * @param x integer value
     */
    public void add(final int x) {
        final short hb = Util.highbits(x);
        final int i = highLowContainer.getIndex(hb);
        if (i >= 0) {
            highLowContainer.setContainerAtIndex(i,highLowContainer.getContainerAtIndex(i).add(Util.lowbits(x))
            );
        } else {
            final ArrayContainer newac = new ArrayContainer();
            highLowContainer.insertNewKeyValueAt(-i - 1, hb, newac.add(Util.lowbits(x)));
        }
    }

    /**
     * In-place bitwise AND (intersection) operation. The current bitmap is
     * modified.
     *
     * @param x2 other bitmap
     */
    public void and(final RoaringBitmap x2) {
        int pos1 = 0, pos2 = 0;
        int length1 = highLowContainer.size();
        final int length2 = x2.highLowContainer.size();
                /*
                 * TODO: This could be optimized quite a bit when one bitmap is
                 * much smaller than the other one.
                 */
        main:
        if (pos1 < length1 && pos2 < length2) {
            short s1 = highLowContainer.getKeyAtIndex(pos1);
            short s2 = x2.highLowContainer.getKeyAtIndex(pos2);
            do {
                if (s1 < s2) {
                    highLowContainer.removeAtIndex(pos1);
                    --length1;
                    if (pos1 == length1)
                        break main;
                    s1 = highLowContainer.getKeyAtIndex(pos1);
                } else if (s1 > s2) {
                    pos2++;
                    if (pos2 == length2)
                        break main;
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                } else {
                    final Container c = highLowContainer.getContainerAtIndex(pos1).iand(
                            x2.highLowContainer.getContainerAtIndex(pos2));
                    if (c.getCardinality() > 0) {
                        this.highLowContainer.setContainerAtIndex(pos1, c);
                        pos1++;
                    } else {
                        highLowContainer.removeAtIndex(pos1);
                        --length1;
                    }
                    pos2++;
                    if ((pos1 == length1) || (pos2 == length2))
                        break main;
                    s1 = highLowContainer.getKeyAtIndex(pos1);
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                }
            } while (true);
        }
        highLowContainer.resize(pos1);
    }

    /**
     * In-place bitwise ANDNOT (difference) operation. The current bitmap is
     * modified.
     *
     * @param x2 other bitmap
     */
    public void andNot(final RoaringBitmap x2) {
        int pos1 = 0, pos2 = 0;
        int length1 = highLowContainer.size();
        final int length2 = x2.highLowContainer.size();
        main:
        if (pos1 < length1 && pos2 < length2) {
            short s1 = highLowContainer.getKeyAtIndex(pos1);
            short s2 = x2.highLowContainer.getKeyAtIndex(pos2);
            do {
                if (s1 < s2) {
                    pos1++;
                    if (pos1 == length1)
                        break main;
                    s1 = highLowContainer.getKeyAtIndex(pos1);
                } else if (s1 > s2) {
                    pos2++;
                    if (pos2 == length2) {
                        break main;
                    }
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                } else {
                    final Container c = highLowContainer.getContainerAtIndex(pos1).iandNot(
                            x2.highLowContainer.getContainerAtIndex(pos2)
                    );
                    if (c.getCardinality() > 0) {
                        this.highLowContainer.setContainerAtIndex(pos1, c);
                        pos1++;
                    } else {
                        highLowContainer.removeAtIndex(pos1);
                        --length1;
                    }
                    pos2++;
                    if ((pos1 == length1) || (pos2 == length2))
                        break main;
                    s1 = highLowContainer.getKeyAtIndex(pos1);
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                }
            } while (true);
        }
    }

    /**
     * reset to an empty bitmap; result occupies as much space a newly
     * created bitmap.
     */
    public void clear() {
        highLowContainer = new RoaringArray(); // lose references
    }

    @Override
    public RoaringBitmap clone() {
        try {
            final RoaringBitmap x = (RoaringBitmap) super.clone();
            x.highLowContainer = highLowContainer.clone();
            return x;
        } catch (final CloneNotSupportedException e) {
            throw new RuntimeException("shouldn't happen with clone", e);
        }
    }

    /**
     * Deserialize the bitmap (retrieve from the input stream).
     * The current bitmap is overwritten.
     *
     * @param in the DataInput stream
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void deserialize(DataInput in) throws IOException {
        this.highLowContainer.deserialize(in);
    }

    /**
     * Modifies the current bitmap by complementing the bits in the given
     * range, from rangeStart (inclusive) rangeEnd (exclusive).
     *
     * @param rangeStart inclusive beginning of range
     * @param rangeEnd   exclusive ending of range
     */
    public void flip(final int rangeStart, final int rangeEnd) {
        if (rangeStart >= rangeEnd)
            return; // empty range

        final short hbStart = Util.highbits(rangeStart);
        final short lbStart = Util.lowbits(rangeStart);
        final short hbLast = Util.highbits(rangeEnd - 1);
        final short lbLast = Util.lowbits(rangeEnd - 1);

        final int max = Util.toIntUnsigned(Util.maxLowBit());
        for (short hb = hbStart; hb <= hbLast; ++hb) {
            // first container may contain partial range
            final int containerStart = (hb == hbStart) ? Util.toIntUnsigned(lbStart) : 0;
            // last container may contain partial range
            final int containerLast = (hb == hbLast) ? Util.toIntUnsigned(lbLast) : max;
            final int i = highLowContainer.getIndex(hb);

            if (i >= 0) {
                final Container c = highLowContainer
                        .getContainerAtIndex(i).inot(
                                containerStart, containerLast);
                if (c.getCardinality() > 0)
                    highLowContainer.setContainerAtIndex(i, c);
                else
                    highLowContainer.removeAtIndex(i);
            } else {
                highLowContainer.insertNewKeyValueAt(-i - 1,
                        hb, Container.rangeOfOnes(
                                containerStart, containerLast)
                );
            }
        }
    }

    private IntIterator getIntIterator() {
        return new IntIterator() {
            int hs = 0;

            Iterator<Short> iter;

            short pos = 0;

            int x;

            @Override
            public boolean hasNext() {
                return pos < RoaringBitmap.this.highLowContainer.size();
            }

            public IntIterator init() {
                if (pos < RoaringBitmap.this.highLowContainer.size()) {
                    iter = RoaringBitmap.this.highLowContainer
                            .getContainerAtIndex(pos)
                            .iterator();
                    hs = Util
                            .toIntUnsigned(RoaringBitmap.this.highLowContainer
                                    .getKeyAtIndex(pos)) << 16;
                }
                return this;
            }

            @Override
            public int next() {
                x = Util.toIntUnsigned(iter.next()) | hs;
                if (!iter.hasNext()) {
                    ++pos;
                    init();
                }
                return x;
            }

            @Override
            public void remove() {
                if ((x & hs) == hs) {// still in same container
                    iter.remove();
                } else {
                    RoaringBitmap.this.remove(x);
                }
            }

        }.init();
    }

    @Override
    public int hashCode() {
        return highLowContainer.hashCode();
    }

    /**
     * iterate over the positions of the true values.
     *
     * @return the iterator
     */
    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            int hs = 0;

            Iterator<Short> iter;

            short pos = 0;

            int x;

            @Override
            public boolean hasNext() {
                return pos < RoaringBitmap.this.highLowContainer.size();
            }

            public Iterator<Integer> init() {
                if (pos < RoaringBitmap.this.highLowContainer.size()) {
                    iter = RoaringBitmap.this.highLowContainer
                            .getContainerAtIndex(pos)
                            .iterator();
                    hs = Util
                            .toIntUnsigned(RoaringBitmap.this.highLowContainer
                                    .getKeyAtIndex(pos)) << 16;
                }
                return this;
            }

            @Override
            public Integer next() {
                x = Util.toIntUnsigned(iter.next()) | hs;
                if (!iter.hasNext()) {
                    ++pos;
                    init();
                }
                return x;
            }

            @Override
            public void remove() {
                if ((x & hs) == hs) {// still in same container
                    iter.remove();
                } else {
                    RoaringBitmap.this.remove(x);
                }
            }

        }.init();
    }

    /**
     * In-place bitwise OR (union) operation. The current bitmap is
     * modified.
     *
     * @param x2 other bitmap
     */
    public void or(final RoaringBitmap x2) {
        int pos1 = 0, pos2 = 0;
        int length1 = highLowContainer.size();
        final int length2 = x2.highLowContainer.size();
        main:
        if (pos1 < length1 && pos2 < length2) {
            short s1 = highLowContainer.getKeyAtIndex(pos1);
            short s2 = x2.highLowContainer.getKeyAtIndex(pos2);

            while (true) {
                if (s1 < s2) {
                    pos1++;
                    if (pos1 == length1) {
                        break main;
                    }
                    s1 = highLowContainer.getKeyAtIndex(pos1);
                } else if (s1 > s2) {
                    highLowContainer
                            .insertNewKeyValueAt(
                                    pos1,
                                    s2,
                                    x2.highLowContainer
                                            .getContainerAtIndex(pos2)
                            );
                    pos1++;
                    length1++;
                    pos2++;
                    if (pos2 == length2) {
                        break main;
                    }
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                } else {
                    this.highLowContainer
                            .setContainerAtIndex(
                                    pos1,
                                    highLowContainer
                                            .getContainerAtIndex(
                                                    pos1)
                                            .ior(x2.highLowContainer
                                                    .getContainerAtIndex(pos2))
                            );
                    pos1++;
                    pos2++;
                    if ((pos1 == length1) || (pos2 == length2)) {
                        break main;
                    }
                    s1 = highLowContainer.getKeyAtIndex(pos1);
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                }
            }
        }
        if (pos1 == length1) {
            highLowContainer.appendCopy(x2.highLowContainer, pos2, length2);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        this.highLowContainer.readExternal(in);

    }

    /**
     * If present remove the specified integers (effectively, sets its bit
     * value to false)
     *
     * @param x integer value representing the index in a bitmap
     */
    public void remove(final int x) {
        final short hb = Util.highbits(x);
        final int i = highLowContainer.getIndex(hb);
        if (i < 0)
            return;
        highLowContainer.setContainerAtIndex(i, highLowContainer
                .getContainerAtIndex(i).remove(Util.lowbits(x)));
        if (highLowContainer.getContainerAtIndex(i).getCardinality() == 0)
            highLowContainer.removeAtIndex(i);
    }

    /**
     * Serialize this bitmap.
     * <p/>
     * The current bitmap is not modified.
     *
     * @param out the DataOutput stream
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void serialize(DataOutput out) throws IOException {
        this.highLowContainer.serialize(out);
    }

    /**
     * A string describing the bitmap.
     *
     * @return the string
     */
    @Override
    public String toString() {
        final StringBuilder answer = new StringBuilder();
        final IntIterator i = this.getIntIterator();
        answer.append("{");
        if (i.hasNext())
            answer.append(i.next());
        while (i.hasNext()) {
            answer.append(",");
            answer.append(i.next());
        }
        answer.append("}");
        return answer.toString();
    }

    /**
     * Recover allocated but unused memory.
     */
    public void trim() {
        for (int i = 0; i < this.highLowContainer.size(); i++) {
            this.highLowContainer.getContainerAtIndex(i).trim();
        }
    }


    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        this.highLowContainer.writeExternal(out);
    }

    /**
     * In-place bitwise XOR (symmetric difference) operation. The current
     * bitmap is modified.
     *
     * @param x2 other bitmap
     */
    public void xor(final RoaringBitmap x2) {
        int pos1 = 0, pos2 = 0;
        int length1 = highLowContainer.size();
        final int length2 = x2.highLowContainer.size();

        main:
        if (pos1 < length1 && pos2 < length2) {
            short s1 = highLowContainer.getKeyAtIndex(pos1);
            short s2 = x2.highLowContainer.getKeyAtIndex(pos2);

            while (true) {
                if (s1 < s2) {
                    pos1++;
                    if (pos1 == length1) {
                        break main;
                    }
                    s1 = highLowContainer.getKeyAtIndex(pos1);
                } else if (s1 > s2) {
                    highLowContainer.insertNewKeyValueAt(pos1, s2,
                            x2.highLowContainer.getContainerAtIndex(pos2)
                    );
                    pos1++;
                    length1++;
                    pos2++;
                    if (pos2 == length2) {
                        break main;
                    }
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                } else {
                    final Container c = highLowContainer.getContainerAtIndex(pos1).ixor(
                            x2.highLowContainer.getContainerAtIndex(pos2));
                    if (c.getCardinality() > 0) {
                        this.highLowContainer.setContainerAtIndex(pos1, c);
                        pos1++;
                    } else {
                        highLowContainer.removeAtIndex(pos1);
                        --length1;
                    }
                    pos2++;
                    if ((pos1 == length1) || (pos2 == length2)) {
                        break main;
                    }
                    s1 = highLowContainer.getKeyAtIndex(pos1);
                    s2 = x2.highLowContainer.getKeyAtIndex(pos2);
                }
            }
        }
        if (pos1 == length1) {
            highLowContainer.appendCopy(x2.highLowContainer, pos2, length2);
        }
    }
}
