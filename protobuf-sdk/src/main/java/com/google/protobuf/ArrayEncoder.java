package com.google.protobuf;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.google.protobuf.WireFormat.MAX_VARINT_SIZE;

/** A {@link CodedOutputStream} that writes directly to a byte array. */
class ArrayEncoder extends CodedOutputStream {
    private final byte[] buffer;
    private final int offset;
    private final int limit;
    private int position;

    ArrayEncoder(byte[] buffer, int offset, int length) {
        super();
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        if ((offset | length | (buffer.length - (offset + length))) < 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "Array range is invalid. Buffer.length=%d, offset=%d, length=%d",
                            buffer.length, offset, length));
        }
        this.buffer = buffer;
        this.offset = offset;
        position = offset;
        limit = offset + length;
    }

    @Override
    public final void writeTag(final int fieldNumber, final int wireType) throws IOException {
        writeUInt32NoTag(WireFormat.makeTag(fieldNumber, wireType));
    }

    @Override
    public final void writeInt32(final int fieldNumber, final int value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        writeInt32NoTag(value);
    }

    @Override
    public final void writeUInt32(final int fieldNumber, final int value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        writeUInt32NoTag(value);
    }

    @Override
    public final void writeFixed32(final int fieldNumber, final int value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED32);
        writeFixed32NoTag(value);
    }

    @Override
    public final void writeUInt64(final int fieldNumber, final long value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        writeUInt64NoTag(value);
    }

    @Override
    public final void writeFixed64(final int fieldNumber, final long value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED64);
        writeFixed64NoTag(value);
    }

    @Override
    public final void writeBool(final int fieldNumber, final boolean value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        write((byte) (value ? 1 : 0));
    }

    @Override
    public final void writeString(final int fieldNumber, final String value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeStringNoTag(value);
    }

    @Override
    public final void writeBytes(final int fieldNumber, final ByteString value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeBytesNoTag(value);
    }

    @Override
    public final void writeByteArray(final int fieldNumber, final byte[] value) throws IOException {
        writeByteArray(fieldNumber, value, 0, value.length);
    }

    @Override
    public final void writeByteArray(
            final int fieldNumber, final byte[] value, final int offset, final int length)
            throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeByteArrayNoTag(value, offset, length);
    }

    @Override
    public final void writeByteBuffer(final int fieldNumber, final ByteBuffer value)
            throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeUInt32NoTag(value.capacity());
        writeRawBytes(value);
    }

    @Override
    public final void writeBytesNoTag(final ByteString value) throws IOException {
        writeUInt32NoTag(value.size());
        value.writeTo(this);
    }

    @Override
    public final void writeByteArrayNoTag(final byte[] value, int offset, int length)
            throws IOException {
        writeUInt32NoTag(length);
        write(value, offset, length);
    }

    @Override
    public final void writeRawBytes(final ByteBuffer value) throws IOException {
        if (value.hasArray()) {
            write(value.array(), value.arrayOffset(), value.capacity());
        } else {
            ByteBuffer duplicated = value.duplicate();
            Java8Compatibility.clear(duplicated);
            write(duplicated);
        }
    }

    @Override
    public final void writeMessage(final int fieldNumber, final MessageLite value)
            throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeMessageNoTag(value);
    }

    @Override
    final void writeMessage(final int fieldNumber, final MessageLite value, Schema schema)
            throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeUInt32NoTag(((AbstractMessageLite) value).getSerializedSize(schema));
        schema.writeTo(value, wrapper);
    }

    @Override
    public final void writeMessageSetExtension(final int fieldNumber, final MessageLite value)
            throws IOException {
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_START_GROUP);
        writeUInt32(WireFormat.MESSAGE_SET_TYPE_ID, fieldNumber);
        writeMessage(WireFormat.MESSAGE_SET_MESSAGE, value);
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_END_GROUP);
    }

    @Override
    public final void writeRawMessageSetExtension(final int fieldNumber, final ByteString value)
            throws IOException {
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_START_GROUP);
        writeUInt32(WireFormat.MESSAGE_SET_TYPE_ID, fieldNumber);
        writeBytes(WireFormat.MESSAGE_SET_MESSAGE, value);
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_END_GROUP);
    }

    @Override
    public final void writeMessageNoTag(final MessageLite value) throws IOException {
        writeUInt32NoTag(value.getSerializedSize());
        ((AbstractMessageLite) value).writeTo(this);
    }

    @Override
    final void writeMessageNoTag(final MessageLite value, Schema schema) throws IOException {
        writeUInt32NoTag(((AbstractMessageLite) value).getSerializedSize(schema));
        schema.writeTo(value, wrapper);
    }

    @Override
    public final void write(byte value) throws IOException {
        try {
            buffer[position++] = value;
        } catch (IndexOutOfBoundsException e) {
            throw new OutOfSpaceException(
                    String.format("Pos: %d, limit: %d, len: %d", position, limit, 1), e);
        }
    }

    @Override
    public final void writeInt32NoTag(int value) throws IOException {
        if (value >= 0) {
            writeUInt32NoTag(value);
        } else {
            // Must sign-extend.
            writeUInt64NoTag(value);
        }
    }

    @Override
    public final void writeUInt32NoTag(int value) throws IOException {
        try {
            while (true) {
                if ((value & ~0x7F) == 0) {
                    buffer[position++] = (byte) value;
                    return;
                } else {
                    buffer[position++] = (byte) ((value & 0x7F) | 0x80);
                    value >>>= 7;
                }
            }
        } catch (IndexOutOfBoundsException e) {
            throw new OutOfSpaceException(
                    String.format("Pos: %d, limit: %d, len: %d", position, limit, 1), e);
        }
    }

    @Override
    public final void writeFixed32NoTag(int value) throws IOException {
        try {
            buffer[position++] = (byte) (value & 0xFF);
            buffer[position++] = (byte) ((value >> 8) & 0xFF);
            buffer[position++] = (byte) ((value >> 16) & 0xFF);
            buffer[position++] = (byte) ((value >> 24) & 0xFF);
        } catch (IndexOutOfBoundsException e) {
            throw new OutOfSpaceException(
                    String.format("Pos: %d, limit: %d, len: %d", position, limit, 1), e);
        }
    }

    @Override
    public final void writeUInt64NoTag(long value) throws IOException {
        if (HAS_UNSAFE_ARRAY_OPERATIONS && spaceLeft() >= MAX_VARINT_SIZE) {
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    UnsafeUtil.putByte(buffer, position++, (byte) value);
                    return;
                } else {
                    UnsafeUtil.putByte(buffer, position++, (byte) (((int) value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
        } else {
            try {
                while (true) {
                    if ((value & ~0x7FL) == 0) {
                        buffer[position++] = (byte) value;
                        return;
                    } else {
                        buffer[position++] = (byte) (((int) value & 0x7F) | 0x80);
                        value >>>= 7;
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                throw new OutOfSpaceException(
                        String.format("Pos: %d, limit: %d, len: %d", position, limit, 1), e);
            }
        }
    }

    @Override
    public final void writeFixed64NoTag(long value) throws IOException {
        try {
            buffer[position++] = (byte) ((int) (value) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 8) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 16) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 24) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 32) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 40) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 48) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 56) & 0xFF);
        } catch (IndexOutOfBoundsException e) {
            throw new OutOfSpaceException(
                    String.format("Pos: %d, limit: %d, len: %d", position, limit, 1), e);
        }
    }

    @Override
    public final void write(byte[] value, int offset, int length) throws IOException {
        try {
            System.arraycopy(value, offset, buffer, position, length);
            position += length;
        } catch (IndexOutOfBoundsException e) {
            throw new OutOfSpaceException(
                    String.format("Pos: %d, limit: %d, len: %d", position, limit, length), e);
        }
    }

    @Override
    public final void writeLazy(byte[] value, int offset, int length) throws IOException {
        write(value, offset, length);
    }

    @Override
    public final void write(ByteBuffer value) throws IOException {
        final int length = value.remaining();
        try {
            value.get(buffer, position, length);
            position += length;
        } catch (IndexOutOfBoundsException e) {
            throw new OutOfSpaceException(
                    String.format("Pos: %d, limit: %d, len: %d", position, limit, length), e);
        }
    }

    @Override
    public final void writeLazy(ByteBuffer value) throws IOException {
        write(value);
    }

    @Override
    public final void writeStringNoTag(String value) throws IOException {
        final int oldPosition = position;
        try {
            // UTF-8 byte length of the string is at least its UTF-16 code unit length (value.length()),
            // and at most 3 times of it. We take advantage of this in both branches below.
            final int maxLength = value.length() * Utf8.MAX_BYTES_PER_CHAR;
            final int maxLengthVarIntSize = computeUInt32SizeNoTag(maxLength);
            final int minLengthVarIntSize = computeUInt32SizeNoTag(value.length());
            if (minLengthVarIntSize == maxLengthVarIntSize) {
                position = oldPosition + minLengthVarIntSize;
                int newPosition = Utf8.encode(value, buffer, position, spaceLeft());
                // Since this class is stateful and tracks the position, we rewind and store the state,
                // prepend the length, then reset it back to the end of the string.
                position = oldPosition;
                int length = newPosition - oldPosition - minLengthVarIntSize;
                writeUInt32NoTag(length);
                position = newPosition;
            } else {
                int length = Utf8.encodedLength(value);
                writeUInt32NoTag(length);
                position = Utf8.encode(value, buffer, position, spaceLeft());
            }
        } catch (Utf8.UnpairedSurrogateException e) {
            // Roll back the change - we fall back to inefficient path.
            position = oldPosition;

            // TODO: We should throw an IOException here instead.
            inefficientWriteStringNoTag(value, e);
        } catch (IndexOutOfBoundsException e) {
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void flush() {
        // Do nothing.
    }

    @Override
    public final int spaceLeft() {
        return limit - position;
    }

    @Override
    public final int getTotalBytesWritten() {
        return position - offset;
    }
}