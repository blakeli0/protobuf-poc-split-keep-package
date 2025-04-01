package com.google.protobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.google.protobuf.Internal.EMPTY_BYTE_BUFFER;
import static com.google.protobuf.Internal.UTF_8;
import static com.google.protobuf.WireFormat.*;
import static com.google.protobuf.WireFormat.FIXED64_SIZE;


/** A {@link CodedInputStream} implementation that uses a backing array as the input. */
final class ArrayDecoder extends CodedInputStream {
    private final byte[] buffer;
    private final boolean immutable;
    private int limit;
    private int bufferSizeAfterLimit;
    private int pos;
    private int startPos;
    private int lastTag;
    private boolean enableAliasing;

    /** The absolute position of the end of the current message. */
    private int currentLimit = Integer.MAX_VALUE;

    ArrayDecoder(final byte[] buffer, final int offset, final int len, boolean immutable) {
        super();
        this.buffer = buffer;
        limit = offset + len;
        pos = offset;
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
        if (size > 0 && size <= (limit - pos)) {
            // Fast path:  We already have the bytes in a contiguous buffer, so
            //   just copy directly from it.
            final String result = new String(buffer, pos, size, UTF_8);
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
        if (size > 0 && size <= (limit - pos)) {
            String result = Utf8.decodeUtf8(buffer, pos, size);
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
        if (size > 0 && size <= (limit - pos)) {
            // Fast path:  We already have the bytes in a contiguous buffer, so
            //   just copy directly from it.
            final ByteString result =
                    immutable && enableAliasing
                            ? ByteString.wrap(buffer, pos, size)
                            : ByteString.copyFrom(buffer, pos, size);
            pos += size;
            return result;
        }
        if (size == 0) {
            return ByteString.EMPTY;
        }
        // Slow path:  Build a byte array first then copy it.
        return ByteString.wrap(readRawBytes(size));
    }

    @Override
    public byte[] readByteArray() throws IOException {
        final int size = readRawVarint32();
        return readRawBytes(size);
    }

    @Override
    public ByteBuffer readByteBuffer() throws IOException {
        final int size = readRawVarint32();
        if (size > 0 && size <= (limit - pos)) {
            // Fast path: We already have the bytes in a contiguous buffer.
            // When aliasing is enabled, we can return a ByteBuffer pointing directly
            // into the underlying byte array without copy if the CodedInputStream is
            // constructed from a byte array. If aliasing is disabled or the input is
            // from an InputStream or ByteString, we have to make a copy of the bytes.
            ByteBuffer result =
                    !immutable && enableAliasing
                            ? ByteBuffer.wrap(buffer, pos, size).slice()
                            : ByteBuffer.wrap(Arrays.copyOfRange(buffer, pos, pos + size));
            pos += size;
            // TODO: Investigate making the ByteBuffer be made read-only
            return result;
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
            int tempPos = pos;

            if (limit == tempPos) {
                break fastpath;
            }

            final byte[] buffer = this.buffer;
            int x;
            if ((x = buffer[tempPos++]) >= 0) {
                pos = tempPos;
                return x;
            } else if (limit - tempPos < 9) {
                break fastpath;
            } else if ((x ^= (buffer[tempPos++] << 7)) < 0) {
                x ^= (~0 << 7);
            } else if ((x ^= (buffer[tempPos++] << 14)) >= 0) {
                x ^= (~0 << 7) ^ (~0 << 14);
            } else if ((x ^= (buffer[tempPos++] << 21)) < 0) {
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            } else {
                int y = buffer[tempPos++];
                x ^= y << 28;
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0
                        && buffer[tempPos++] < 0
                        && buffer[tempPos++] < 0
                        && buffer[tempPos++] < 0
                        && buffer[tempPos++] < 0
                        && buffer[tempPos++] < 0) {
                    break fastpath; // Will throw malformedVarint()
                }
            }
            pos = tempPos;
            return x;
        }
        return (int) readRawVarint64SlowPath();
    }

    private void skipRawVarint() throws IOException {
        if (limit - pos >= MAX_VARINT_SIZE) {
            skipRawVarintFastPath();
        } else {
            skipRawVarintSlowPath();
        }
    }

    private void skipRawVarintFastPath() throws IOException {
        for (int i = 0; i < MAX_VARINT_SIZE; i++) {
            if (buffer[pos++] >= 0) {
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
            int tempPos = pos;

            if (limit == tempPos) {
                break fastpath;
            }

            final byte[] buffer = this.buffer;
            long x;
            int y;
            if ((y = buffer[tempPos++]) >= 0) {
                pos = tempPos;
                return y;
            } else if (limit - tempPos < 9) {
                break fastpath;
            } else if ((y ^= (buffer[tempPos++] << 7)) < 0) {
                x = y ^ (~0 << 7);
            } else if ((y ^= (buffer[tempPos++] << 14)) >= 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14));
            } else if ((y ^= (buffer[tempPos++] << 21)) < 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            } else if ((x = y ^ ((long) buffer[tempPos++] << 28)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((x ^= ((long) buffer[tempPos++] << 35)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((x ^= ((long) buffer[tempPos++] << 42)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((x ^= ((long) buffer[tempPos++] << 49)) < 0L) {
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49);
            } else {
                x ^= ((long) buffer[tempPos++] << 56);
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
                    if (buffer[tempPos++] < 0L) {
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
        int tempPos = pos;

        if (limit - tempPos < FIXED32_SIZE) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }

        final byte[] buffer = this.buffer;
        pos = tempPos + FIXED32_SIZE;
        return ((buffer[tempPos] & 0xff)
                | ((buffer[tempPos + 1] & 0xff) << 8)
                | ((buffer[tempPos + 2] & 0xff) << 16)
                | ((buffer[tempPos + 3] & 0xff) << 24));
    }

    @Override
    public long readRawLittleEndian64() throws IOException {
        int tempPos = pos;

        if (limit - tempPos < FIXED64_SIZE) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }

        final byte[] buffer = this.buffer;
        pos = tempPos + FIXED64_SIZE;
        return ((buffer[tempPos] & 0xffL)
                | ((buffer[tempPos + 1] & 0xffL) << 8)
                | ((buffer[tempPos + 2] & 0xffL) << 16)
                | ((buffer[tempPos + 3] & 0xffL) << 24)
                | ((buffer[tempPos + 4] & 0xffL) << 32)
                | ((buffer[tempPos + 5] & 0xffL) << 40)
                | ((buffer[tempPos + 6] & 0xffL) << 48)
                | ((buffer[tempPos + 7] & 0xffL) << 56));
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
        if (byteLimit < 0) {
            throw InvalidProtocolBufferException.parseFailure();
        }
        final int oldLimit = currentLimit;
        if (byteLimit > oldLimit) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }
        currentLimit = byteLimit;

        recomputeBufferSizeAfterLimit();

        return oldLimit;
    }

    private void recomputeBufferSizeAfterLimit() {
        limit += bufferSizeAfterLimit;
        final int bufferEnd = limit - startPos;
        if (bufferEnd > currentLimit) {
            // Limit is in current buffer.
            bufferSizeAfterLimit = bufferEnd - currentLimit;
            limit -= bufferSizeAfterLimit;
        } else {
            bufferSizeAfterLimit = 0;
        }
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
        return pos - startPos;
    }

    @Override
    public byte readRawByte() throws IOException {
        if (pos == limit) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }
        return buffer[pos++];
    }

    @Override
    public byte[] readRawBytes(final int length) throws IOException {
        if (length > 0 && length <= (limit - pos)) {
            final int tempPos = pos;
            pos += length;
            return Arrays.copyOfRange(buffer, tempPos, pos);
        }

        if (length <= 0) {
            if (length == 0) {
                return Internal.EMPTY_BYTE_ARRAY;
            } else {
                throw InvalidProtocolBufferException.negativeSize();
            }
        }
        throw InvalidProtocolBufferException.truncatedMessage();
    }

    @Override
    public void skipRawBytes(final int length) throws IOException {
        if (length >= 0 && length <= (limit - pos)) {
            // We have all the bytes we need already.
            pos += length;
            return;
        }

        if (length < 0) {
            throw InvalidProtocolBufferException.negativeSize();
        }
        throw InvalidProtocolBufferException.truncatedMessage();
    }
}