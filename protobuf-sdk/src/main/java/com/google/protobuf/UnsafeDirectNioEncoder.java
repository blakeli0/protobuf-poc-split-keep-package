package com.google.protobuf;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.google.protobuf.WireFormat.*;

/**
 * A {@link CodedOutputStream} that writes directly to a direct {@link ByteBuffer} using {@code
 * sun.misc.Unsafe}.
 */
final class UnsafeDirectNioEncoder extends CodedOutputStream {
    private final ByteBuffer originalBuffer;
    private final ByteBuffer buffer;
    private final long address;
    private final long initialPosition;
    private final long limit;
    private final long oneVarintLimit;
    private long position;

    UnsafeDirectNioEncoder(ByteBuffer buffer) {
        this.originalBuffer = buffer;
        this.buffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        address = UnsafeUtil.addressOffset(buffer);
        initialPosition = address + buffer.position();
        limit = address + buffer.limit();
        oneVarintLimit = limit - MAX_VARINT_SIZE;
        position = initialPosition;
    }

    static boolean isSupported() {
        return UnsafeUtil.hasUnsafeByteBufferOperations();
    }

    @Override
    public void writeTag(int fieldNumber, int wireType) throws IOException {
        writeUInt32NoTag(WireFormat.makeTag(fieldNumber, wireType));
    }

    @Override
    public void writeInt32(int fieldNumber, int value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        writeInt32NoTag(value);
    }

    @Override
    public void writeUInt32(int fieldNumber, int value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        writeUInt32NoTag(value);
    }

    @Override
    public void writeFixed32(int fieldNumber, int value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED32);
        writeFixed32NoTag(value);
    }

    @Override
    public void writeUInt64(int fieldNumber, long value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        writeUInt64NoTag(value);
    }

    @Override
    public void writeFixed64(int fieldNumber, long value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED64);
        writeFixed64NoTag(value);
    }

    @Override
    public void writeBool(int fieldNumber, boolean value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        write((byte) (value ? 1 : 0));
    }

    @Override
    public void writeString(int fieldNumber, String value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeStringNoTag(value);
    }

    @Override
    public void writeBytes(int fieldNumber, ByteString value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeBytesNoTag(value);
    }

    @Override
    public void writeByteArray(int fieldNumber, byte[] value) throws IOException {
        writeByteArray(fieldNumber, value, 0, value.length);
    }

    @Override
    public void writeByteArray(int fieldNumber, byte[] value, int offset, int length)
            throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeByteArrayNoTag(value, offset, length);
    }

    @Override
    public void writeByteBuffer(int fieldNumber, ByteBuffer value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeUInt32NoTag(value.capacity());
        writeRawBytes(value);
    }

    @Override
    public void writeMessage(int fieldNumber, MessageLite value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeMessageNoTag(value);
    }

    @Override
    void writeMessage(int fieldNumber, MessageLite value, Schema schema) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeMessageNoTag(value, schema);
    }

    @Override
    public void writeMessageSetExtension(int fieldNumber, MessageLite value) throws IOException {
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_START_GROUP);
        writeUInt32(WireFormat.MESSAGE_SET_TYPE_ID, fieldNumber);
        writeMessage(WireFormat.MESSAGE_SET_MESSAGE, value);
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_END_GROUP);
    }

    @Override
    public void writeRawMessageSetExtension(int fieldNumber, ByteString value) throws IOException {
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_START_GROUP);
        writeUInt32(WireFormat.MESSAGE_SET_TYPE_ID, fieldNumber);
        writeBytes(WireFormat.MESSAGE_SET_MESSAGE, value);
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_END_GROUP);
    }

    @Override
    public void writeMessageNoTag(MessageLite value) throws IOException {
        writeUInt32NoTag(value.getSerializedSize());
        value.writeTo(this);
    }

    @Override
    void writeMessageNoTag(MessageLite value, Schema schema) throws IOException {
        writeUInt32NoTag(((AbstractMessageLite) value).getSerializedSize(schema));
        schema.writeTo(value, wrapper);
    }

    @Override
    public void write(byte value) throws IOException {
        if (position >= limit) {
            throw new OutOfSpaceException(
                    String.format("Pos: %d, limit: %d, len: %d", position, limit, 1));
        }
        UnsafeUtil.putByte(position++, value);
    }

    @Override
    public void writeBytesNoTag(ByteString value) throws IOException {
        writeUInt32NoTag(value.size());
        value.writeTo(this);
    }

    @Override
    public void writeByteArrayNoTag(byte[] value, int offset, int length) throws IOException {
        writeUInt32NoTag(length);
        write(value, offset, length);
    }

    @Override
    public void writeRawBytes(ByteBuffer value) throws IOException {
        if (value.hasArray()) {
            write(value.array(), value.arrayOffset(), value.capacity());
        } else {
            ByteBuffer duplicated = value.duplicate();
            Java8Compatibility.clear(duplicated);
            write(duplicated);
        }
    }

    @Override
    public void writeInt32NoTag(int value) throws IOException {
        if (value >= 0) {
            writeUInt32NoTag(value);
        } else {
            // Must sign-extend.
            writeUInt64NoTag(value);
        }
    }

    @Override
    public void writeUInt32NoTag(int value) throws IOException {
        if (position <= oneVarintLimit) {
            // Optimization to avoid bounds checks on each iteration.
            while (true) {
                if ((value & ~0x7F) == 0) {
                    UnsafeUtil.putByte(position++, (byte) value);
                    return;
                } else {
                    UnsafeUtil.putByte(position++, (byte) ((value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
        } else {
            while (position < limit) {
                if ((value & ~0x7F) == 0) {
                    UnsafeUtil.putByte(position++, (byte) value);
                    return;
                } else {
                    UnsafeUtil.putByte(position++, (byte) ((value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
            throw new OutOfSpaceException(
                    String.format("Pos: %d, limit: %d, len: %d", position, limit, 1));
        }
    }

    @Override
    public void writeFixed32NoTag(int value) throws IOException {
        buffer.putInt(bufferPos(position), value);
        position += FIXED32_SIZE;
    }

    @Override
    public void writeUInt64NoTag(long value) throws IOException {
        if (position <= oneVarintLimit) {
            // Optimization to avoid bounds checks on each iteration.
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    UnsafeUtil.putByte(position++, (byte) value);
                    return;
                } else {
                    UnsafeUtil.putByte(position++, (byte) (((int) value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
        } else {
            while (position < limit) {
                if ((value & ~0x7FL) == 0) {
                    UnsafeUtil.putByte(position++, (byte) value);
                    return;
                } else {
                    UnsafeUtil.putByte(position++, (byte) (((int) value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
            throw new OutOfSpaceException(
                    String.format("Pos: %d, limit: %d, len: %d", position, limit, 1));
        }
    }

    @Override
    public void writeFixed64NoTag(long value) throws IOException {
        buffer.putLong(bufferPos(position), value);
        position += FIXED64_SIZE;
    }

    @Override
    public void write(byte[] value, int offset, int length) throws IOException {
        if (value == null
                || offset < 0
                || length < 0
                || (value.length - length) < offset
                || (limit - length) < position) {
            if (value == null) {
                throw new NullPointerException("value");
            }
            throw new OutOfSpaceException(
                    String.format("Pos: %d, limit: %d, len: %d", position, limit, length));
        }

        UnsafeUtil.copyMemory(value, offset, position, length);
        position += length;
    }

    @Override
    public void writeLazy(byte[] value, int offset, int length) throws IOException {
        write(value, offset, length);
    }

    @Override
    public void write(ByteBuffer value) throws IOException {
        try {
            int length = value.remaining();
            repositionBuffer(position);
            buffer.put(value);
            position += length;
        } catch (BufferOverflowException e) {
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void writeLazy(ByteBuffer value) throws IOException {
        write(value);
    }

    @Override
    public void writeStringNoTag(String value) throws IOException {
        long prevPos = position;
        try {
            // UTF-8 byte length of the string is at least its UTF-16 code unit length (value.length()),
            // and at most 3 times of it. We take advantage of this in both branches below.
            int maxEncodedSize = value.length() * Utf8.MAX_BYTES_PER_CHAR;
            int maxLengthVarIntSize = computeUInt32SizeNoTag(maxEncodedSize);
            int minLengthVarIntSize = computeUInt32SizeNoTag(value.length());
            if (minLengthVarIntSize == maxLengthVarIntSize) {
                // Save the current position and increment past the length field. We'll come back
                // and write the length field after the encoding is complete.
                int stringStart = bufferPos(position) + minLengthVarIntSize;
                Java8Compatibility.position(buffer, stringStart);

                // Encode the string.
                Utf8.encodeUtf8(value, buffer);

                // Write the length and advance the position.
                int length = buffer.position() - stringStart;
                writeUInt32NoTag(length);
                position += length;
            } else {
                // Calculate and write the encoded length.
                int length = Utf8.encodedLength(value);
                writeUInt32NoTag(length);

                // Write the string and advance the position.
                repositionBuffer(position);
                Utf8.encodeUtf8(value, buffer);
                position += length;
            }
        } catch (Utf8.UnpairedSurrogateException e) {
            // Roll back the change and convert to an IOException.
            position = prevPos;
            repositionBuffer(position);

            // TODO: We should throw an IOException here instead.
            inefficientWriteStringNoTag(value, e);
        } catch (IllegalArgumentException e) {
            // Thrown by buffer.position() if out of range.
            throw new OutOfSpaceException(e);
        } catch (IndexOutOfBoundsException e) {
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void flush() {
        // Update the position of the original buffer.
        Java8Compatibility.position(originalBuffer, bufferPos(position));
    }

    @Override
    public int spaceLeft() {
        return (int) (limit - position);
    }

    @Override
    public int getTotalBytesWritten() {
        return (int) (position - initialPosition);
    }

    private void repositionBuffer(long pos) {
        Java8Compatibility.position(buffer, bufferPos(pos));
    }

    private int bufferPos(long pos) {
        return (int) (pos - address);
    }
}