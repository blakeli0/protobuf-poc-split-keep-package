package com.google.protobuf;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import static com.google.protobuf.Internal.*;
import static com.google.protobuf.WireFormat.*;
import static com.google.protobuf.WireFormat.FIXED64_SIZE;

/**
 * A {@link CodedInputStream} implementation that uses a backing direct ByteBuffer as the input.
 * Requires the use of {@code sun.misc.Unsafe} to perform fast reads on the buffer.
 */
final class UnsafeDirectNioDecoder extends CodedInputStream {
    /** The direct buffer that is backing this stream. */
    private final ByteBuffer buffer;

    /**
     * If {@code true}, indicates that the buffer is backing a {@link ByteString} and is therefore
     * considered to be an immutable input source.
     */
    private final boolean immutable;

    /** The unsafe address of the content of {@link #buffer}. */
    private final long address;

    /** The unsafe address of the current read limit of the buffer. */
    private long limit;

    /** The unsafe address of the current read position of the buffer. */
    private long pos;

    /** The unsafe address of the starting read position. */
    private long startPos;

    /** The amount of available data in the buffer beyond {@link #limit}. */
    private int bufferSizeAfterLimit;

    /** The last tag that was read from this stream. */
    private int lastTag;

    /**
     * If {@code true}, indicates that calls to read {@link ByteString} or {@code byte[]}
     * <strong>may</strong> return slices of the underlying buffer, rather than copies.
     */
    private boolean enableAliasing;

    /** The absolute position of the end of the current message. */
    private int currentLimit = Integer.MAX_VALUE;

    static boolean isSupported() {
        return UnsafeUtil.hasUnsafeByteBufferOperations();
    }

    UnsafeDirectNioDecoder(ByteBuffer buffer, boolean immutable) {
        this.buffer = buffer;
        address = UnsafeUtil.addressOffset(buffer);
        limit = address + buffer.limit();
        pos = address + buffer.position();
        startPos = pos;
        this.immutable = immutable;
    }

    @Override
    public int readTag() throws IOException {
        if (isAtEnd()) {
            lastTag = 0;
            return 0;
        }

        lastTag = readRawVarint32();
        if (WireFormat.getTagFieldNumber(lastTag) == 0) {
            // If we actually read zero (or any tag number corresponding to field
            // number zero), that's not a valid tag.
            throw InvalidProtocolBufferException.invalidTag();
        }
        return lastTag;
    }

    @Override
    public void checkLastTagWas(final int value) throws InvalidProtocolBufferException {
        if (lastTag != value) {
            throw InvalidProtocolBufferException.invalidEndTag();
        }
    }

    @Override
    public int getLastTag() {
        return lastTag;
    }

    @Override
    public boolean skipField(final int tag) throws IOException {
        switch (WireFormat.getTagWireType(tag)) {
            case WireFormat.WIRETYPE_VARINT:
                skipRawVarint();
                return true;
            case WireFormat.WIRETYPE_FIXED64:
                skipRawBytes(FIXED64_SIZE);
                return true;
            case WireFormat.WIRETYPE_LENGTH_DELIMITED:
                skipRawBytes(readRawVarint32());
                return true;
            case WireFormat.WIRETYPE_START_GROUP:
                skipMessage();
                checkLastTagWas(
                        WireFormat.makeTag(WireFormat.getTagFieldNumber(tag), WireFormat.WIRETYPE_END_GROUP));
                return true;
            case WireFormat.WIRETYPE_END_GROUP:
                return false;
            case WireFormat.WIRETYPE_FIXED32:
                skipRawBytes(FIXED32_SIZE);
                return true;
            default:
                throw InvalidProtocolBufferException.invalidWireType();
        }
    }

    @Override
    public boolean skipField(final int tag, final CodedOutputStream output) throws IOException {
        switch (WireFormat.getTagWireType(tag)) {
            case WireFormat.WIRETYPE_VARINT:
            {
                long value = readInt64();
                output.writeUInt32NoTag(tag);
                output.writeUInt64NoTag(value);
                return true;
            }
            case WireFormat.WIRETYPE_FIXED64:
            {
                long value = readRawLittleEndian64();
                output.writeUInt32NoTag(tag);
                output.writeFixed64NoTag(value);
                return true;
            }
            case WireFormat.WIRETYPE_LENGTH_DELIMITED:
            {
                ByteString value = readBytes();
                output.writeUInt32NoTag(tag);
                output.writeBytesNoTag(value);
                return true;
            }
            case WireFormat.WIRETYPE_START_GROUP:
            {
                output.writeUInt32NoTag(tag);
                skipMessage(output);
                int endtag =
                        WireFormat.makeTag(
                                WireFormat.getTagFieldNumber(tag), WireFormat.WIRETYPE_END_GROUP);
                checkLastTagWas(endtag);
                output.writeUInt32NoTag(endtag);
                return true;
            }
            case WireFormat.WIRETYPE_END_GROUP:
            {
                return false;
            }
            case WireFormat.WIRETYPE_FIXED32:
            {
                int value = readRawLittleEndian32();
                output.writeUInt32NoTag(tag);
                output.writeFixed32NoTag(value);
                return true;
            }
            default:
                throw InvalidProtocolBufferException.invalidWireType();
        }
    }

    // -----------------------------------------------------------------

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readRawLittleEndian64());
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readRawLittleEndian32());
    }

    @Override
    public long readUInt64() throws IOException {
        return readRawVarint64();
    }

    @Override
    public long readInt64() throws IOException {
        return readRawVarint64();
    }

    @Override
    public int readInt32() throws IOException {
        return readRawVarint32();
    }

    @Override
    public long readFixed64() throws IOException {
        return readRawLittleEndian64();
    }

    @Override
    public int readFixed32() throws IOException {
        return readRawLittleEndian32();
    }

    @Override
    public boolean readBool() throws IOException {
        return readRawVarint64() != 0;
    }

    @Override
    public String readString() throws IOException {
        final int size = readRawVarint32();
        if (size > 0 && size <= remaining()) {
            // TODO: Is there a way to avoid this copy?
            // TODO: It might be possible to share the optimized loop with
            // readStringRequireUtf8 by implementing Java replacement logic there.
            // The same as readBytes' logic
            byte[] bytes = new byte[size];
            UnsafeUtil.copyMemory(pos, bytes, 0, size);
            String result = new String(bytes, UTF_8);
            pos += size;
            return result;
        }

        if (size == 0) {
            return "";
        }
        if (size < 0) {
            throw InvalidProtocolBufferException.negativeSize();
        }
        throw InvalidProtocolBufferException.truncatedMessage();
    }

    @Override
    public String readStringRequireUtf8() throws IOException {
        final int size = readRawVarint32();
        if (size > 0 && size <= remaining()) {
            final int bufferPos = bufferPos(pos);
            String result = Utf8.decodeUtf8(buffer, bufferPos, size);
            pos += size;
            return result;
        }

        if (size == 0) {
            return "";
        }
        if (size <= 0) {
            throw InvalidProtocolBufferException.negativeSize();
        }
        throw InvalidProtocolBufferException.truncatedMessage();
    }

    @Override
    public void readGroup(
            final int fieldNumber,
            final MessageLite.Builder builder,
            final ExtensionRegistryLite extensionRegistry)
            throws IOException {
        checkRecursionLimit();
        ++recursionDepth;
        builder.mergeFrom(this, extensionRegistry);
        checkLastTagWas(WireFormat.makeTag(fieldNumber, WireFormat.WIRETYPE_END_GROUP));
        --recursionDepth;
    }

    @Override
    public <T extends MessageLite> T readGroup(
            final int fieldNumber,
            final Parser<T> parser,
            final ExtensionRegistryLite extensionRegistry)
            throws IOException {
        checkRecursionLimit();
        ++recursionDepth;
        T result = parser.parsePartialFrom(this, extensionRegistry);
        checkLastTagWas(WireFormat.makeTag(fieldNumber, WireFormat.WIRETYPE_END_GROUP));
        --recursionDepth;
        return result;
    }

    @Deprecated
    @Override
    public void readUnknownGroup(final int fieldNumber, final MessageLite.Builder builder)
            throws IOException {
        readGroup(fieldNumber, builder, ExtensionRegistryLite.getEmptyRegistry());
    }

    @Override
    public void readMessage(
            final MessageLite.Builder builder, final ExtensionRegistryLite extensionRegistry)
            throws IOException {
        final int length = readRawVarint32();
        checkRecursionLimit();
        final int oldLimit = pushLimit(length);
        ++recursionDepth;
        builder.mergeFrom(this, extensionRegistry);
        checkLastTagWas(0);
        --recursionDepth;
        if (getBytesUntilLimit() != 0) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }
        popLimit(oldLimit);
    }

    @Override
    public <T extends MessageLite> T readMessage(
            final Parser<T> parser, final ExtensionRegistryLite extensionRegistry) throws IOException {
        int length = readRawVarint32();
        checkRecursionLimit();
        final int oldLimit = pushLimit(length);
        ++recursionDepth;
        T result = parser.parsePartialFrom(this, extensionRegistry);
        checkLastTagWas(0);
        --recursionDepth;
        if (getBytesUntilLimit() != 0) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }
        popLimit(oldLimit);
        return result;
    }

    @Override
    public ByteString readBytes() throws IOException {
        final int size = readRawVarint32();
        if (size > 0 && size <= remaining()) {
            if (immutable && enableAliasing) {
                final ByteBuffer result = slice(pos, pos + size);
                pos += size;
                return ByteString.wrap(result);
            } else {
                // Use UnsafeUtil to copy the memory to bytes instead of using ByteBuffer ways.
                byte[] bytes = new byte[size];
                UnsafeUtil.copyMemory(pos, bytes, 0, size);
                pos += size;
                return ByteString.wrap(bytes);
            }
        }

        if (size == 0) {
            return ByteString.EMPTY;
        }
        if (size < 0) {
            throw InvalidProtocolBufferException.negativeSize();
        }
        throw InvalidProtocolBufferException.truncatedMessage();
    }

    @Override
    public byte[] readByteArray() throws IOException {
        return readRawBytes(readRawVarint32());
    }

    @Override
    public ByteBuffer readByteBuffer() throws IOException {
        final int size = readRawVarint32();
        if (size > 0 && size <= remaining()) {
            // "Immutable" implies that buffer is backing a ByteString.
            // Disallow slicing in this case to prevent the caller from modifying the contents
            // of the ByteString.
            if (!immutable && enableAliasing) {
                final ByteBuffer result = slice(pos, pos + size);
                pos += size;
                return result;
            } else {
                // The same as readBytes' logic
                byte[] bytes = new byte[size];
                UnsafeUtil.copyMemory(pos, bytes, 0, size);
                pos += size;
                return ByteBuffer.wrap(bytes);
            }
            // TODO: Investigate making the ByteBuffer be made read-only
        }

        if (size == 0) {
            return EMPTY_BYTE_BUFFER;
        }
        if (size < 0) {
            throw InvalidProtocolBufferException.negativeSize();
        }
        throw InvalidProtocolBufferException.truncatedMessage();
    }

    @Override
    public int readUInt32() throws IOException {
        return readRawVarint32();
    }

    @Override
    public int readEnum() throws IOException {
        return readRawVarint32();
    }

    @Override
    public int readSFixed32() throws IOException {
        return readRawLittleEndian32();
    }

    @Override
    public long readSFixed64() throws IOException {
        return readRawLittleEndian64();
    }

    @Override
    public int readSInt32() throws IOException {
        return decodeZigZag32(readRawVarint32());
    }

    @Override
    public long readSInt64() throws IOException {
        return decodeZigZag64(readRawVarint64());
    }

    // =================================================================

    @Override
    public int readRawVarint32() throws IOException {
        // See implementation notes for readRawVarint64
        fastpath:
        {
            long tempPos = pos;

            if (limit == tempPos) {
                break fastpath;
            }

            int x;
            if ((x = UnsafeUtil.getByte(tempPos++)) >= 0) {
                pos = tempPos;
                return x;
            } else if (limit - tempPos < 9) {
                break fastpath;
            } else if ((x ^= (UnsafeUtil.getByte(tempPos++) << 7)) < 0) {
                x ^= (~0 << 7);
            } else if ((x ^= (UnsafeUtil.getByte(tempPos++) << 14)) >= 0) {
                x ^= (~0 << 7) ^ (~0 << 14);
            } else if ((x ^= (UnsafeUtil.getByte(tempPos++) << 21)) < 0) {
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            } else {
                int y = UnsafeUtil.getByte(tempPos++);
                x ^= y << 28;
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0
                        && UnsafeUtil.getByte(tempPos++) < 0
                        && UnsafeUtil.getByte(tempPos++) < 0
                        && UnsafeUtil.getByte(tempPos++) < 0
                        && UnsafeUtil.getByte(tempPos++) < 0
                        && UnsafeUtil.getByte(tempPos++) < 0) {
                    break fastpath; // Will throw malformedVarint()
                }
            }
            pos = tempPos;
            return x;
        }
        return (int) readRawVarint64SlowPath();
    }

    private void skipRawVarint() throws IOException {
        if (remaining() >= MAX_VARINT_SIZE) {
            skipRawVarintFastPath();
        } else {
            skipRawVarintSlowPath();
        }
    }

    private void skipRawVarintFastPath() throws IOException {
        for (int i = 0; i < MAX_VARINT_SIZE; i++) {
            if (UnsafeUtil.getByte(pos++) >= 0) {
                return;
            }
        }
        throw InvalidProtocolBufferException.malformedVarint();
    }

    private void skipRawVarintSlowPath() throws IOException {
        for (int i = 0; i < MAX_VARINT_SIZE; i++) {
            if (readRawByte() >= 0) {
                return;
            }
        }
        throw InvalidProtocolBufferException.malformedVarint();
    }

    @Override
    public long readRawVarint64() throws IOException {
        // Implementation notes:
        //
        // Optimized for one-byte values, expected to be common.
        // The particular code below was selected from various candidates
        // empirically, by winning VarintBenchmark.
        //
        // Sign extension of (signed) Java bytes is usually a nuisance, but
        // we exploit it here to more easily obtain the sign of bytes read.
        // Instead of cleaning up the sign extension bits by masking eagerly,
        // we delay until we find the final (positive) byte, when we clear all
        // accumulated bits with one xor.  We depend on javac to constant fold.
        fastpath:
        {
            long tempPos = pos;

            if (limit == tempPos) {
                break fastpath;
            }

            long x;
            int y;
            if ((y = UnsafeUtil.getByte(tempPos++)) >= 0) {
                pos = tempPos;
                return y;
            } else if (limit - tempPos < 9) {
                break fastpath;
            } else if ((y ^= (UnsafeUtil.getByte(tempPos++) << 7)) < 0) {
                x = y ^ (~0 << 7);
            } else if ((y ^= (UnsafeUtil.getByte(tempPos++) << 14)) >= 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14));
            } else if ((y ^= (UnsafeUtil.getByte(tempPos++) << 21)) < 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            } else if ((x = y ^ ((long) UnsafeUtil.getByte(tempPos++) << 28)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((x ^= ((long) UnsafeUtil.getByte(tempPos++) << 35)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((x ^= ((long) UnsafeUtil.getByte(tempPos++) << 42)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((x ^= ((long) UnsafeUtil.getByte(tempPos++) << 49)) < 0L) {
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49);
            } else {
                x ^= ((long) UnsafeUtil.getByte(tempPos++) << 56);
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49)
                                ^ (~0L << 56);
                if (x < 0L) {
                    if (UnsafeUtil.getByte(tempPos++) < 0L) {
                        break fastpath; // Will throw malformedVarint()
                    }
                }
            }
            pos = tempPos;
            return x;
        }
        return readRawVarint64SlowPath();
    }

    @Override
    long readRawVarint64SlowPath() throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = readRawByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw InvalidProtocolBufferException.malformedVarint();
    }

    @Override
    public int readRawLittleEndian32() throws IOException {
        long tempPos = pos;

        if (limit - tempPos < FIXED32_SIZE) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }

        pos = tempPos + FIXED32_SIZE;
        return ((UnsafeUtil.getByte(tempPos) & 0xff)
                | ((UnsafeUtil.getByte(tempPos + 1) & 0xff) << 8)
                | ((UnsafeUtil.getByte(tempPos + 2) & 0xff) << 16)
                | ((UnsafeUtil.getByte(tempPos + 3) & 0xff) << 24));
    }

    @Override
    public long readRawLittleEndian64() throws IOException {
        long tempPos = pos;

        if (limit - tempPos < FIXED64_SIZE) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }

        pos = tempPos + FIXED64_SIZE;
        return ((UnsafeUtil.getByte(tempPos) & 0xffL)
                | ((UnsafeUtil.getByte(tempPos + 1) & 0xffL) << 8)
                | ((UnsafeUtil.getByte(tempPos + 2) & 0xffL) << 16)
                | ((UnsafeUtil.getByte(tempPos + 3) & 0xffL) << 24)
                | ((UnsafeUtil.getByte(tempPos + 4) & 0xffL) << 32)
                | ((UnsafeUtil.getByte(tempPos + 5) & 0xffL) << 40)
                | ((UnsafeUtil.getByte(tempPos + 6) & 0xffL) << 48)
                | ((UnsafeUtil.getByte(tempPos + 7) & 0xffL) << 56));
    }

    @Override
    public void enableAliasing(boolean enabled) {
        this.enableAliasing = enabled;
    }

    @Override
    public void resetSizeCounter() {
        startPos = pos;
    }

    @Override
    public int pushLimit(int byteLimit) throws InvalidProtocolBufferException {
        if (byteLimit < 0) {
            throw InvalidProtocolBufferException.negativeSize();
        }
        byteLimit += getTotalBytesRead();
        final int oldLimit = currentLimit;
        if (byteLimit > oldLimit) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }
        currentLimit = byteLimit;

        recomputeBufferSizeAfterLimit();

        return oldLimit;
    }

    @Override
    public void popLimit(final int oldLimit) {
        currentLimit = oldLimit;
        recomputeBufferSizeAfterLimit();
    }

    @Override
    public int getBytesUntilLimit() {
        if (currentLimit == Integer.MAX_VALUE) {
            return -1;
        }

        return currentLimit - getTotalBytesRead();
    }

    @Override
    public boolean isAtEnd() throws IOException {
        return pos == limit;
    }

    @Override
    public int getTotalBytesRead() {
        return (int) (pos - startPos);
    }

    @Override
    public byte readRawByte() throws IOException {
        if (pos == limit) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }
        return UnsafeUtil.getByte(pos++);
    }

    @Override
    public byte[] readRawBytes(final int length) throws IOException {
        if (length >= 0 && length <= remaining()) {
            byte[] bytes = new byte[length];
            slice(pos, pos + length).get(bytes);
            pos += length;
            return bytes;
        }

        if (length <= 0) {
            if (length == 0) {
                return EMPTY_BYTE_ARRAY;
            } else {
                throw InvalidProtocolBufferException.negativeSize();
            }
        }

        throw InvalidProtocolBufferException.truncatedMessage();
    }

    @Override
    public void skipRawBytes(final int length) throws IOException {
        if (length >= 0 && length <= remaining()) {
            // We have all the bytes we need already.
            pos += length;
            return;
        }

        if (length < 0) {
            throw InvalidProtocolBufferException.negativeSize();
        }
        throw InvalidProtocolBufferException.truncatedMessage();
    }

    private void recomputeBufferSizeAfterLimit() {
        limit += bufferSizeAfterLimit;
        final int bufferEnd = (int) (limit - startPos);
        if (bufferEnd > currentLimit) {
            // Limit is in current buffer.
            bufferSizeAfterLimit = bufferEnd - currentLimit;
            limit -= bufferSizeAfterLimit;
        } else {
            bufferSizeAfterLimit = 0;
        }
    }

    private int remaining() {
        return (int) (limit - pos);
    }

    private int bufferPos(long pos) {
        return (int) (pos - address);
    }

    private ByteBuffer slice(long begin, long end) throws IOException {
        int prevPos = buffer.position();
        int prevLimit = buffer.limit();
        // View ByteBuffer as Buffer to avoid cross-Java version issues.
        // See https://issues.apache.org/jira/browse/MRESOLVER-85
        Buffer asBuffer = buffer;
        try {
            asBuffer.position(bufferPos(begin));
            asBuffer.limit(bufferPos(end));
            return buffer.slice();
        } catch (IllegalArgumentException e) {
            InvalidProtocolBufferException ex = InvalidProtocolBufferException.truncatedMessage();
            ex.initCause(e);
            throw ex;
        } finally {
            asBuffer.position(prevPos);
            asBuffer.limit(prevLimit);
        }
    }
}