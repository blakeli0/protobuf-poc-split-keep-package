package com.google.protobuf;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;

import static com.google.protobuf.Internal.*;
import static com.google.protobuf.WireFormat.*;

/**
 * Implementation of {@link CodedInputStream} that uses an {@link Iterable <ByteBuffer>} as the
 * data source. Requires the use of {@code sun.misc.Unsafe} to perform fast reads on the buffer.
 */
final class IterableDirectByteBufferDecoder extends CodedInputStream {
    /** The object that need to decode. */
    private final Iterable<ByteBuffer> input;
    /** The {@link Iterator} with type {@link ByteBuffer} of {@code input} */
    private final Iterator<ByteBuffer> iterator;
    /** The current ByteBuffer; */
    private ByteBuffer currentByteBuffer;
    /**
     * If {@code true}, indicates that all the buffers are backing a {@link ByteString} and are
     * therefore considered to be an immutable input source.
     */
    private final boolean immutable;
    /**
     * If {@code true}, indicates that calls to read {@link ByteString} or {@code byte[]}
     * <strong>may</strong> return slices of the underlying buffer, rather than copies.
     */
    private boolean enableAliasing;
    /** The global total message length limit */
    private int totalBufferSize;
    /** The amount of available data in the input beyond {@link #currentLimit}. */
    private int bufferSizeAfterCurrentLimit;
    /** The absolute position of the end of the current message. */
    private int currentLimit = Integer.MAX_VALUE;
    /** The last tag that was read from this stream. */
    private int lastTag;
    /** Total Bytes have been Read from the {@link Iterable} {@link ByteBuffer} */
    private int totalBytesRead;
    /** The start position offset of the whole message, used as to reset the totalBytesRead */
    private int startOffset;
    /** The current position for current ByteBuffer */
    private long currentByteBufferPos;

    private long currentByteBufferStartPos;
    /**
     * If the current ByteBuffer is unsafe-direct based, currentAddress is the start address of this
     * ByteBuffer; otherwise should be zero.
     */
    private long currentAddress;
    /** The limit position for current ByteBuffer */
    private long currentByteBufferLimit;

    /**
     * The constructor of {@code Iterable<ByteBuffer>} decoder.
     *
     * @param inputBufs The input data.
     * @param size The total size of the input data.
     * @param immutableFlag whether the input data is immutable.
     */
    IterableDirectByteBufferDecoder(
            Iterable<ByteBuffer> inputBufs, int size, boolean immutableFlag) {
        totalBufferSize = size;
        input = inputBufs;
        iterator = input.iterator();
        immutable = immutableFlag;
        startOffset = totalBytesRead = 0;
        if (size == 0) {
            currentByteBuffer = EMPTY_BYTE_BUFFER;
            currentByteBufferPos = 0;
            currentByteBufferStartPos = 0;
            currentByteBufferLimit = 0;
            currentAddress = 0;
        } else {
            tryGetNextByteBuffer();
        }
    }

    /** To get the next ByteBuffer from {@code input}, and then update the parameters */
    private void getNextByteBuffer() throws InvalidProtocolBufferException {
        if (!iterator.hasNext()) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }
        tryGetNextByteBuffer();
    }

    private void tryGetNextByteBuffer() {
        currentByteBuffer = iterator.next();
        totalBytesRead += (int) (currentByteBufferPos - currentByteBufferStartPos);
        currentByteBufferPos = currentByteBuffer.position();
        currentByteBufferStartPos = currentByteBufferPos;
        currentByteBufferLimit = currentByteBuffer.limit();
        currentAddress = UnsafeUtil.addressOffset(currentByteBuffer);
        currentByteBufferPos += currentAddress;
        currentByteBufferStartPos += currentAddress;
        currentByteBufferLimit += currentAddress;
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
        if (size > 0 && size <= currentByteBufferLimit - currentByteBufferPos) {
            byte[] bytes = new byte[size];
            UnsafeUtil.copyMemory(currentByteBufferPos, bytes, 0, size);
            String result = new String(bytes, UTF_8);
            currentByteBufferPos += size;
            return result;
        } else if (size > 0 && size <= remaining()) {
            // TODO: To use an underlying bytes[] instead of allocating a new bytes[]
            byte[] bytes = new byte[size];
            readRawBytesTo(bytes, 0, size);
            String result = new String(bytes, UTF_8);
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
        if (size > 0 && size <= currentByteBufferLimit - currentByteBufferPos) {
            final int bufferPos = (int) (currentByteBufferPos - currentByteBufferStartPos);
            String result = Utf8.decodeUtf8(currentByteBuffer, bufferPos, size);
            currentByteBufferPos += size;
            return result;
        }
        if (size >= 0 && size <= remaining()) {
            byte[] bytes = new byte[size];
            readRawBytesTo(bytes, 0, size);
            return Utf8.decodeUtf8(bytes, 0, size);
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
        if (size > 0 && size <= currentByteBufferLimit - currentByteBufferPos) {
            if (immutable && enableAliasing) {
                final int idx = (int) (currentByteBufferPos - currentAddress);
                final ByteString result = ByteString.wrap(slice(idx, idx + size));
                currentByteBufferPos += size;
                return result;
            } else {
                byte[] bytes = new byte[size];
                UnsafeUtil.copyMemory(currentByteBufferPos, bytes, 0, size);
                currentByteBufferPos += size;
                return ByteString.wrap(bytes);
            }
        } else if (size > 0 && size <= remaining()) {
            if (immutable && enableAliasing) {
                ArrayList<ByteString> byteStrings = new ArrayList<>();
                int l = size;
                while (l > 0) {
                    if (currentRemaining() == 0) {
                        getNextByteBuffer();
                    }
                    int bytesToCopy = Math.min(l, (int) currentRemaining());
                    int idx = (int) (currentByteBufferPos - currentAddress);
                    byteStrings.add(ByteString.wrap(slice(idx, idx + bytesToCopy)));
                    l -= bytesToCopy;
                    currentByteBufferPos += bytesToCopy;
                }
                return ByteString.copyFrom(byteStrings);
            } else {
                byte[] temp = new byte[size];
                readRawBytesTo(temp, 0, size);
                return ByteString.wrap(temp);
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
        if (size > 0 && size <= currentRemaining()) {
            if (!immutable && enableAliasing) {
                currentByteBufferPos += size;
                return slice(
                        (int) (currentByteBufferPos - currentAddress - size),
                        (int) (currentByteBufferPos - currentAddress));
            } else {
                byte[] bytes = new byte[size];
                UnsafeUtil.copyMemory(currentByteBufferPos, bytes, 0, size);
                currentByteBufferPos += size;
                return ByteBuffer.wrap(bytes);
            }
        } else if (size > 0 && size <= remaining()) {
            byte[] temp = new byte[size];
            readRawBytesTo(temp, 0, size);
            return ByteBuffer.wrap(temp);
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

    @Override
    public int readRawVarint32() throws IOException {
        fastpath:
        {
            long tempPos = currentByteBufferPos;

            if (currentByteBufferLimit == currentByteBufferPos) {
                break fastpath;
            }

            int x;
            if ((x = UnsafeUtil.getByte(tempPos++)) >= 0) {
                currentByteBufferPos++;
                return x;
            } else if (currentByteBufferLimit - currentByteBufferPos < 10) {
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
            currentByteBufferPos = tempPos;
            return x;
        }
        return (int) readRawVarint64SlowPath();
    }

    @Override
    public long readRawVarint64() throws IOException {
        fastpath:
        {
            long tempPos = currentByteBufferPos;

            if (currentByteBufferLimit == currentByteBufferPos) {
                break fastpath;
            }

            long x;
            int y;
            if ((y = UnsafeUtil.getByte(tempPos++)) >= 0) {
                currentByteBufferPos++;
                return y;
            } else if (currentByteBufferLimit - currentByteBufferPos < 10) {
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
            currentByteBufferPos = tempPos;
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
        if (currentRemaining() >= FIXED32_SIZE) {
            long tempPos = currentByteBufferPos;
            currentByteBufferPos += FIXED32_SIZE;
            return ((UnsafeUtil.getByte(tempPos) & 0xff)
                    | ((UnsafeUtil.getByte(tempPos + 1) & 0xff) << 8)
                    | ((UnsafeUtil.getByte(tempPos + 2) & 0xff) << 16)
                    | ((UnsafeUtil.getByte(tempPos + 3) & 0xff) << 24));
        }
        return ((readRawByte() & 0xff)
                | ((readRawByte() & 0xff) << 8)
                | ((readRawByte() & 0xff) << 16)
                | ((readRawByte() & 0xff) << 24));
    }

    @Override
    public long readRawLittleEndian64() throws IOException {
        if (currentRemaining() >= FIXED64_SIZE) {
            long tempPos = currentByteBufferPos;
            currentByteBufferPos += FIXED64_SIZE;
            return ((UnsafeUtil.getByte(tempPos) & 0xffL)
                    | ((UnsafeUtil.getByte(tempPos + 1) & 0xffL) << 8)
                    | ((UnsafeUtil.getByte(tempPos + 2) & 0xffL) << 16)
                    | ((UnsafeUtil.getByte(tempPos + 3) & 0xffL) << 24)
                    | ((UnsafeUtil.getByte(tempPos + 4) & 0xffL) << 32)
                    | ((UnsafeUtil.getByte(tempPos + 5) & 0xffL) << 40)
                    | ((UnsafeUtil.getByte(tempPos + 6) & 0xffL) << 48)
                    | ((UnsafeUtil.getByte(tempPos + 7) & 0xffL) << 56));
        }
        return ((readRawByte() & 0xffL)
                | ((readRawByte() & 0xffL) << 8)
                | ((readRawByte() & 0xffL) << 16)
                | ((readRawByte() & 0xffL) << 24)
                | ((readRawByte() & 0xffL) << 32)
                | ((readRawByte() & 0xffL) << 40)
                | ((readRawByte() & 0xffL) << 48)
                | ((readRawByte() & 0xffL) << 56));
    }

    @Override
    public void enableAliasing(boolean enabled) {
        this.enableAliasing = enabled;
    }

    @Override
    public void resetSizeCounter() {
        startOffset = (int) (totalBytesRead + currentByteBufferPos - currentByteBufferStartPos);
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

    private void recomputeBufferSizeAfterLimit() {
        totalBufferSize += bufferSizeAfterCurrentLimit;
        final int bufferEnd = totalBufferSize - startOffset;
        if (bufferEnd > currentLimit) {
            // Limit is in current buffer.
            bufferSizeAfterCurrentLimit = bufferEnd - currentLimit;
            totalBufferSize -= bufferSizeAfterCurrentLimit;
        } else {
            bufferSizeAfterCurrentLimit = 0;
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
        return totalBytesRead + currentByteBufferPos - currentByteBufferStartPos == totalBufferSize;
    }

    @Override
    public int getTotalBytesRead() {
        return (int)
                (totalBytesRead - startOffset + currentByteBufferPos - currentByteBufferStartPos);
    }

    @Override
    public byte readRawByte() throws IOException {
        if (currentRemaining() == 0) {
            getNextByteBuffer();
        }
        return UnsafeUtil.getByte(currentByteBufferPos++);
    }

    @Override
    public byte[] readRawBytes(final int length) throws IOException {
        if (length >= 0 && length <= currentRemaining()) {
            byte[] bytes = new byte[length];
            UnsafeUtil.copyMemory(currentByteBufferPos, bytes, 0, length);
            currentByteBufferPos += length;
            return bytes;
        }
        if (length >= 0 && length <= remaining()) {
            byte[] bytes = new byte[length];
            readRawBytesTo(bytes, 0, length);
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

    /**
     * Try to get raw bytes from {@code input} with the size of {@code length} and copy to {@code
     * bytes} array. If the size is bigger than the number of remaining bytes in the input, then
     * throw {@code truncatedMessage} exception.
     */
    private void readRawBytesTo(byte[] bytes, int offset, final int length) throws IOException {
        if (length >= 0 && length <= remaining()) {
            int l = length;
            while (l > 0) {
                if (currentRemaining() == 0) {
                    getNextByteBuffer();
                }
                int bytesToCopy = Math.min(l, (int) currentRemaining());
                UnsafeUtil.copyMemory(currentByteBufferPos, bytes, length - l + offset, bytesToCopy);
                l -= bytesToCopy;
                currentByteBufferPos += bytesToCopy;
            }
            return;
        }

        if (length <= 0) {
            if (length == 0) {
                return;
            } else {
                throw InvalidProtocolBufferException.negativeSize();
            }
        }
        throw InvalidProtocolBufferException.truncatedMessage();
    }

    @Override
    public void skipRawBytes(final int length) throws IOException {
        if (length >= 0
                && length
                <= (totalBufferSize
                - totalBytesRead
                - currentByteBufferPos
                + currentByteBufferStartPos)) {
            // We have all the bytes we need already.
            int l = length;
            while (l > 0) {
                if (currentRemaining() == 0) {
                    getNextByteBuffer();
                }
                int rl = Math.min(l, (int) currentRemaining());
                l -= rl;
                currentByteBufferPos += rl;
            }
            return;
        }

        if (length < 0) {
            throw InvalidProtocolBufferException.negativeSize();
        }
        throw InvalidProtocolBufferException.truncatedMessage();
    }

    // TODO: optimize to fastpath
    private void skipRawVarint() throws IOException {
        for (int i = 0; i < MAX_VARINT_SIZE; i++) {
            if (readRawByte() >= 0) {
                return;
            }
        }
        throw InvalidProtocolBufferException.malformedVarint();
    }

    /**
     * Try to get the number of remaining bytes in {@code input}.
     *
     * @return the number of remaining bytes in {@code input}.
     */
    private int remaining() {
        return (int)
                (totalBufferSize - totalBytesRead - currentByteBufferPos + currentByteBufferStartPos);
    }

    /**
     * Try to get the number of remaining bytes in {@code currentByteBuffer}.
     *
     * @return the number of remaining bytes in {@code currentByteBuffer}
     */
    private long currentRemaining() {
        return (currentByteBufferLimit - currentByteBufferPos);
    }

    private ByteBuffer slice(int begin, int end) throws IOException {
        int prevPos = currentByteBuffer.position();
        int prevLimit = currentByteBuffer.limit();
        // View ByteBuffer as Buffer to avoid cross-Java version issues.
        // See https://issues.apache.org/jira/browse/MRESOLVER-85
        Buffer asBuffer = currentByteBuffer;
        try {
            asBuffer.position(begin);
            asBuffer.limit(end);
            return currentByteBuffer.slice();
        } catch (IllegalArgumentException e) {
            throw InvalidProtocolBufferException.truncatedMessage();
        } finally {
            asBuffer.position(prevPos);
            asBuffer.limit(prevLimit);
        }
    }
}