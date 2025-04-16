package com.google.protobuf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

/**
 * This class implements a {@link com.google.protobuf.ByteString} backed by a single array of
 * bytes, contiguous in memory. It supports substring by pointing to only a sub-range of the
 * underlying byte array, meaning that a substring will reference the full byte-array of the
 * string it's made from, exactly as with {@link String}.
 *
 * @author carlanton@google.com (Carl Haverl)
 */
// Keep this class private to avoid deadlocks in classloading across threads as ByteString's
// static initializer loads LiteralByteString and another thread loads LiteralByteString.
class LiteralByteString extends LeafByteString {
    private static final long serialVersionUID = 1L;

    protected final byte[] bytes;

    /**
     * Creates a {@code LiteralByteString} backed by the given array, without copying.
     *
     * @param bytes array to wrap
     */
    LiteralByteString(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException();
        }
        this.bytes = bytes;
    }

    @Override
    public byte byteAt(int index) {
        // Unlike most methods in this class, this one is a direct implementation
        // ignoring the potential offset because we need to do range-checking in the
        // substring case anyway.
        return bytes[index];
    }

    @Override
    byte internalByteAt(int index) {
        return bytes[index];
    }

    @Override
    public int size() {
        return bytes.length;
    }

    // =================================================================
    // ByteString -> substring

    @Override
    public final ByteString substring(int beginIndex, int endIndex) {
        final int length = checkRange(beginIndex, endIndex, size());

        if (length == 0) {
            return ByteString.EMPTY;
        }

        return new BoundedByteString(bytes, getOffsetIntoBytes() + beginIndex, length);
    }

    // =================================================================
    // ByteString -> byte[]

    @Override
    protected void copyToInternal(
            byte[] target, int sourceOffset, int targetOffset, int numberToCopy) {
        // Optimized form, not for subclasses, since we don't call
        // getOffsetIntoBytes() or check the 'numberToCopy' parameter.
        // TODO: Is not calling getOffsetIntoBytes really saving that much?
        System.arraycopy(bytes, sourceOffset, target, targetOffset, numberToCopy);
    }

    @Override
    public final void copyTo(ByteBuffer target) {
        target.put(bytes, getOffsetIntoBytes(), size()); // Copies bytes
    }

    @Override
    public final ByteBuffer asReadOnlyByteBuffer() {
        return ByteBuffer.wrap(bytes, getOffsetIntoBytes(), size()).asReadOnlyBuffer();
    }

    @Override
    public final List<ByteBuffer> asReadOnlyByteBufferList() {
        return Collections.singletonList(asReadOnlyByteBuffer());
    }

    @Override
    public final void writeTo(OutputStream outputStream) throws IOException {
        outputStream.write(toByteArray());
    }

    @Override
    final void writeToInternal(OutputStream outputStream, int sourceOffset, int numberToWrite)
            throws IOException {
        outputStream.write(bytes, getOffsetIntoBytes() + sourceOffset, numberToWrite);
    }

    @Override
    final void writeTo(ByteOutput output) throws IOException {
        output.writeLazy(bytes, getOffsetIntoBytes(), size());
    }

    @Override
    protected final String toStringInternal(Charset charset) {
        return new String(bytes, getOffsetIntoBytes(), size(), charset);
    }

    // =================================================================
    // UTF-8 decoding

    @Override
    public final boolean isValidUtf8() {
        int offset = getOffsetIntoBytes();
        return Utf8.isValidUtf8(bytes, offset, offset + size());
    }

    @Override
    protected final int partialIsValidUtf8(int state, int offset, int length) {
        int index = getOffsetIntoBytes() + offset;
        return Utf8.partialIsValidUtf8(state, bytes, index, index + length);
    }

    // =================================================================
    // equals() and hashCode()

    @Override
    public final boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof ByteString)) {
            return false;
        }

        if (size() != ((ByteString) other).size()) {
            return false;
        }
        if (size() == 0) {
            return true;
        }

        if (other instanceof LiteralByteString) {
            LiteralByteString otherAsLiteral = (LiteralByteString) other;
            // If we know the hash codes and they are not equal, we know the byte
            // strings are not equal.
            int thisHash = peekCachedHashCode();
            int thatHash = otherAsLiteral.peekCachedHashCode();
            if (thisHash != 0 && thatHash != 0 && thisHash != thatHash) {
                return false;
            }

            return equalsRange((LiteralByteString) other, 0, size());
        } else {
            // RopeByteString and NioByteString.
            return other.equals(this);
        }
    }

    /**
     * Check equality of the substring of given length of this object starting at zero with another
     * {@code LiteralByteString} substring starting at offset.
     *
     * @param other what to compare a substring in
     * @param offset offset into other
     * @param length number of bytes to compare
     * @return true for equality of substrings, else false.
     */
    @Override
    final boolean equalsRange(ByteString other, int offset, int length) {
        if (length > other.size()) {
            throw new IllegalArgumentException("Length too large: " + length + size());
        }
        if (offset + length > other.size()) {
            throw new IllegalArgumentException(
                    "Ran off end of other: " + offset + ", " + length + ", " + other.size());
        }

        if (other instanceof LiteralByteString) {
            LiteralByteString lbsOther = (LiteralByteString) other;
            byte[] thisBytes = bytes;
            byte[] otherBytes = lbsOther.bytes;
            int thisLimit = getOffsetIntoBytes() + length;
            for (int thisIndex = getOffsetIntoBytes(),
                 otherIndex = lbsOther.getOffsetIntoBytes() + offset;
                 (thisIndex < thisLimit);
                 ++thisIndex, ++otherIndex) {
                if (thisBytes[thisIndex] != otherBytes[otherIndex]) {
                    return false;
                }
            }
            return true;
        }

        return other.substring(offset, offset + length).equals(substring(0, length));
    }

    @Override
    protected final int partialHash(int h, int offset, int length) {
        return Internal.partialHash(h, bytes, getOffsetIntoBytes() + offset, length);
    }

    // =================================================================
    // Input stream

    @Override
    public final InputStream newInput() {
        return new ByteArrayInputStream(bytes, getOffsetIntoBytes(), size()); // No copy
    }

    @Override
    public final CodedInputStream newCodedInput() {
        // We trust CodedInputStream not to modify the bytes, or to give anyone
        // else access to them.
        return CodedInputStream.newInstance(
                bytes, getOffsetIntoBytes(), size(), /* bufferIsImmutable= */ true);
    }

    // =================================================================
    // Internal methods

    /**
     * Offset into {@code bytes[]} to use, non-zero for substrings.
     *
     * @return always 0 for this class
     */
    protected int getOffsetIntoBytes() {
        return 0;
    }
}
