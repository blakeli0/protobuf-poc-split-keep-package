package com.google.protobuf;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;

import static com.google.protobuf.ByteString.checkIndex;
import static com.google.protobuf.ByteString.checkRange;

/**
 * This class is used to represent the substring of a {@link ByteString} over a single byte array.
 * In terms of the public API of {@link ByteString}, you end up here by calling {@link
 * ByteString#copyFrom(byte[])} followed by {@link ByteString#substring(int, int)}.
 *
 * <p>This class contains most of the overhead involved in creating a substring from a {@link
 * LiteralByteString}. The overhead involves some range-checking and two extra fields.
 *
 * @author carlanton@google.com (Carl Haverl)
 */
// Keep this class private to avoid deadlocks in classloading across threads as ByteString's
// static initializer loads LiteralByteString and another thread loads BoundedByteString.
final class BoundedByteString extends LiteralByteString {
    private final int bytesOffset;
    private final int bytesLength;

    /**
     * Creates a {@code BoundedByteString} backed by the sub-range of given array, without copying.
     *
     * @param bytes array to wrap
     * @param offset index to first byte to use in bytes
     * @param length number of bytes to use from bytes
     * @throws IllegalArgumentException if {@code offset < 0}, {@code length < 0}, or if {@code
     *     offset + length > bytes.length}.
     */
    BoundedByteString(byte[] bytes, int offset, int length) {
        super(bytes);
        checkRange(offset, offset + length, bytes.length);

        this.bytesOffset = offset;
        this.bytesLength = length;
    }

    /**
     * Gets the byte at the given index. Throws {@link ArrayIndexOutOfBoundsException} for
     * backwards-compatibility reasons although it would more properly be {@link
     * IndexOutOfBoundsException}.
     *
     * @param index index of byte
     * @return the value
     * @throws ArrayIndexOutOfBoundsException {@code index} is < 0 or >= size
     */
    @Override
    public byte byteAt(int index) {
        // We must check the index ourselves as we cannot rely on Java array index
        // checking for substrings.
        checkIndex(index, size());
        return bytes[bytesOffset + index];
    }

    @Override
    byte internalByteAt(int index) {
        return bytes[bytesOffset + index];
    }

    @Override
    public int size() {
        return bytesLength;
    }

    @Override
    protected int getOffsetIntoBytes() {
        return bytesOffset;
    }

    // =================================================================
    // ByteString -> byte[]

    @Override
    protected void copyToInternal(
            byte[] target, int sourceOffset, int targetOffset, int numberToCopy) {
        System.arraycopy(
                bytes, getOffsetIntoBytes() + sourceOffset, target, targetOffset, numberToCopy);
    }

    // =================================================================
    // Serializable

    private static final long serialVersionUID = 1L;

    Object writeReplace() {
        return ByteString.wrap(toByteArray());
    }

    private void readObject(@SuppressWarnings("unused") ObjectInputStream in) throws IOException {
        throw new InvalidObjectException(
                "BoundedByteStream instances are not to be serialized directly");
    }
}