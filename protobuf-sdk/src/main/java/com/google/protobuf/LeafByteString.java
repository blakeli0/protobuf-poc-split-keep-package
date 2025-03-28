package com.google.protobuf;

import java.io.IOException;

/** Base class for leaf {@link ByteString}s (i.e. non-ropes). */
abstract class LeafByteString extends ByteString {
    private static final long serialVersionUID = 1L;

    @Override
    protected final int getTreeDepth() {
        return 0;
    }

    @Override
    protected final boolean isBalanced() {
        return true;
    }

    @Override
    void writeToReverse(ByteOutput byteOutput) throws IOException {
        writeTo(byteOutput);
    }

    /**
     * Check equality of the substring of given length of this object starting at zero with another
     * {@code ByteString} substring starting at offset.
     *
     * @param other what to compare a substring in
     * @param offset offset into other
     * @param length number of bytes to compare
     * @return true for equality of substrings, else false.
     */
    abstract boolean equalsRange(ByteString other, int offset, int length);
}