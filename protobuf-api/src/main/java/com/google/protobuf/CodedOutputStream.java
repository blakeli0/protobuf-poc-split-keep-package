// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

package com.google.protobuf;

import com.google.protobuf.Utf8.UnpairedSurrogateException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encodes and writes protocol message fields.
 *
 * <p>This class contains two kinds of methods: methods that write specific protocol message
 * constructs and field types (e.g. {@link #writeTag} and {@link #writeInt32}) and methods that
 * write low-level values (e.g. {@link #writeRawVarint32} and {@link #writeRawBytes}). If you are
 * writing encoded protocol messages, you should use the former methods, but if you are writing some
 * other format of your own design, use the latter.
 *
 * <p>This class is totally unsynchronized.
 */
public abstract class CodedOutputStream extends ByteOutput {
  private static final Logger logger = Logger.getLogger(CodedOutputStream.class.getName());
  static final boolean HAS_UNSAFE_ARRAY_OPERATIONS = UnsafeUtil.hasUnsafeArrayOperations();
  private static final Class<?> ARRAY_ENCODER = getClassForName("com.google.protobuf.ArrayEncoder");
  private static final Class<?> BYTE_OUTPUT_ENCODER = getClassForName("com.google.protobuf.ByteOutputEncoder");
  private static final Class<?> HEAP_NIO_ENCODER = getClassForName("com.google.protobuf.HeapNioEncoder");
  private static final Class<?> OUTPUT_STREAM_ENCODER = getClassForName("com.google.protobuf.OutputStreamEncoder");
  private static final Class<?> SAFE_DIRECT_NIO_ENCODER = getClassForName("com.google.protobuf.SafeDirectNioEncoder");
  private static final Class<?> UNSAFE_DIRECT_NIO_ENCODER = getClassForName("com.google.protobuf.UnsafeDirectNioEncoder");
  /** Used to adapt to the experimental {@link Writer} interface. */
  CodedOutputStreamWriter wrapper;

  // Field numbers for fields in MessageSet wire format.
  static final int MESSAGE_SET_ITEM = 1;
  static final int MESSAGE_SET_TYPE_ID = 2;
  static final int MESSAGE_SET_MESSAGE = 3;

  static final int TAG_TYPE_BITS = 3;

  static final int FIXED32_SIZE = 4;
  static final int FIXED64_SIZE = 8;
  static final int MAX_VARINT_SIZE = 10;

  public static final int WIRETYPE_START_GROUP = 3;
  public static final int WIRETYPE_END_GROUP = 4;


  /** @deprecated Use {@link #computeFixed32SizeNoTag(int)} instead. */
  @Deprecated public static final int LITTLE_ENDIAN_32_SIZE = FIXED32_SIZE;

  /** The buffer size used in {@link #newInstance(OutputStream)}. */
  public static final int DEFAULT_BUFFER_SIZE = 4096;

  private static <T> Class<T> getClassForName(String name) {
    try {
      return (Class<T>) Class.forName(name);
    } catch (Throwable e) {
      return null;
    }
  }
  /**
   * Returns the buffer size to efficiently write dataLength bytes to this CodedOutputStream. Used
   * by AbstractMessageLite.
   *
   * @return the buffer size to efficiently write dataLength bytes to this CodedOutputStream.
   */
  static int computePreferredBufferSize(int dataLength) {
    if (dataLength > DEFAULT_BUFFER_SIZE) {
      return DEFAULT_BUFFER_SIZE;
    }
    return dataLength;
  }

  /**
   * Create a new {@code CodedOutputStream} wrapping the given {@code OutputStream}.
   *
   * <p>NOTE: The provided {@link OutputStream} <strong>MUST NOT</strong> retain access or modify
   * the provided byte arrays. Doing so may result in corrupted data, which would be difficult to
   * debug.
   */
  public static CodedOutputStream newInstance(final OutputStream output) {
    return newInstance(output, DEFAULT_BUFFER_SIZE);
  }

  /**
   * Create a new {@code CodedOutputStream} wrapping the given {@code OutputStream} with a given
   * buffer size.
   *
   * <p>NOTE: The provided {@link OutputStream} <strong>MUST NOT</strong> retain access or modify
   * the provided byte arrays. Doing so may result in corrupted data, which would be difficult to
   * debug.
   */
  public static CodedOutputStream newInstance(final OutputStream output, final int bufferSize) {
    try {
      Constructor<?> constructor = OUTPUT_STREAM_ENCODER.getDeclaredConstructors()[0];
      return (CodedOutputStream)constructor.newInstance(output, bufferSize);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a new {@code CodedOutputStream} that writes directly to the given byte array. If more
   * bytes are written than fit in the array, {@link OutOfSpaceException} will be thrown. Writing
   * directly to a flat array is faster than writing to an {@code OutputStream}. See also {@link
   * ByteString#newCodedBuilder}.
   */
  public static CodedOutputStream newInstance(final byte[] flatArray) {
    return newInstance(flatArray, 0, flatArray.length);
  }

  /**
   * Create a new {@code CodedOutputStream} that writes directly to the given byte array slice. If
   * more bytes are written than fit in the slice, {@link OutOfSpaceException} will be thrown.
   * Writing directly to a flat array is faster than writing to an {@code OutputStream}. See also
   * {@link ByteString#newCodedBuilder}.
   */
  public static CodedOutputStream newInstance(
      final byte[] flatArray, final int offset, final int length) {
      try {
          Constructor<?> constructor = ARRAY_ENCODER.getDeclaredConstructors()[0];
          return (CodedOutputStream)constructor.newInstance(flatArray, offset, length);
      } catch (InstantiationException e) {
          throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
          throw new RuntimeException(e);
      }
  }

  /** Create a new {@code CodedOutputStream} that writes to the given {@link ByteBuffer}. */
  public static CodedOutputStream newInstance(ByteBuffer buffer) {
    if (buffer.hasArray()) {
      try {
        Constructor<?> constructor = HEAP_NIO_ENCODER.getDeclaredConstructors()[0];
        return (CodedOutputStream)constructor.newInstance(buffer);
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
    if (buffer.isDirect() && !buffer.isReadOnly()) {
      return UnsafeUtil.hasUnsafeByteBufferOperations()
          ? newUnsafeInstance(buffer)
          : newSafeInstance(buffer);
    }
    throw new IllegalArgumentException("ByteBuffer is read-only");
  }

  /** For testing purposes only. */
  static CodedOutputStream newUnsafeInstance(ByteBuffer buffer) {
    try {
      Constructor<?> constructor = UNSAFE_DIRECT_NIO_ENCODER.getDeclaredConstructors()[0];
      return (CodedOutputStream)constructor.newInstance(buffer);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** For testing purposes only. */
  static CodedOutputStream newSafeInstance(ByteBuffer buffer) {
    try {
      Constructor<?> constructor = SAFE_DIRECT_NIO_ENCODER.getDeclaredConstructors()[0];
      return (CodedOutputStream)constructor.newInstance(buffer);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Configures serialization to be deterministic.
   *
   * <p>The deterministic serialization guarantees that for a given binary, equal (defined by the
   * {@code equals()} methods in protos) messages will always be serialized to the same bytes. This
   * implies:
   *
   * <ul>
   *   <li>repeated serialization of a message will return the same bytes
   *   <li>different processes of the same binary (which may be executing on different machines)
   *       will serialize equal messages to the same bytes.
   * </ul>
   *
   * <p>Note the deterministic serialization is NOT canonical across languages; it is also unstable
   * across different builds with schema changes due to unknown fields. Users who need canonical
   * serialization, e.g. persistent storage in a canonical form, fingerprinting, etc, should define
   * their own canonicalization specification and implement the serializer using reflection APIs
   * rather than relying on this API.
   *
   * <p>Once set, the serializer will: (Note this is an implementation detail and may subject to
   * change in the future)
   *
   * <ul>
   *   <li>sort map entries by keys in lexicographical order or numerical order. Note: For string
   *       keys, the order is based on comparing the Unicode value of each character in the strings.
   *       The order may be different from the deterministic serialization in other languages where
   *       maps are sorted on the lexicographical order of the UTF8 encoded keys.
   * </ul>
   */
  public void useDeterministicSerialization() {
    serializationDeterministic = true;
  }

  boolean isSerializationDeterministic() {
    return serializationDeterministic;
  }

  private boolean serializationDeterministic;

  /**
   * Create a new {@code CodedOutputStream} that writes to the given {@link ByteBuffer}.
   *
   * @deprecated the size parameter is no longer used since use of an internal buffer is useless
   *     (and wasteful) when writing to a {@link ByteBuffer}. Use {@link #newInstance(ByteBuffer)}
   *     instead.
   */
  @Deprecated
  public static CodedOutputStream newInstance(
      ByteBuffer byteBuffer, @SuppressWarnings("unused") int unused) {
    return newInstance(byteBuffer);
  }

  /**
   * Create a new {@code CodedOutputStream} that writes to the provided {@link ByteOutput}.
   *
   * <p>NOTE: The {@link ByteOutput} <strong>MUST NOT</strong> modify the provided buffers. Doing so
   * may result in corrupted data, which would be difficult to debug.
   *
   * @param byteOutput the output target for encoded bytes.
   * @param bufferSize the size of the internal scratch buffer to be used for string encoding.
   *     Setting this to {@code 0} will disable buffering, requiring an allocation for each encoded
   *     string.
   */
  static CodedOutputStream newInstance(ByteOutput byteOutput, int bufferSize) {
    if (bufferSize < 0) {
      throw new IllegalArgumentException("bufferSize must be positive");
    }
    try {
      Constructor<?> constructor = BYTE_OUTPUT_ENCODER.getDeclaredConstructors()[0];
      return (CodedOutputStream)constructor.newInstance(byteOutput, bufferSize);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  // Disallow construction outside of this class.
  CodedOutputStream() {}

  // -----------------------------------------------------------------

  /** Encode and write a tag. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeTag(int fieldNumber, int wireType) throws IOException;

  /** Write an {@code int32} field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeInt32(int fieldNumber, int value) throws IOException;

  /** Write a {@code uint32} field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeUInt32(int fieldNumber, int value) throws IOException;

  /** Write a {@code sint32} field, including tag, to the stream. */
  public final void writeSInt32(final int fieldNumber, final int value) throws IOException {
    writeUInt32(fieldNumber, encodeZigZag32(value));
  }

  /** Write a {@code fixed32} field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeFixed32(int fieldNumber, int value) throws IOException;

  /** Write an {@code sfixed32} field, including tag, to the stream. */
  public final void writeSFixed32(final int fieldNumber, final int value) throws IOException {
    writeFixed32(fieldNumber, value);
  }

  /** Write an {@code int64} field, including tag, to the stream. */
  public final void writeInt64(final int fieldNumber, final long value) throws IOException {
    writeUInt64(fieldNumber, value);
  }

  /** Write a {@code uint64} field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeUInt64(int fieldNumber, long value) throws IOException;

  /** Write an {@code sint64} field, including tag, to the stream. */
  public final void writeSInt64(final int fieldNumber, final long value) throws IOException {
    writeUInt64(fieldNumber, encodeZigZag64(value));
  }

  /** Write a {@code fixed64} field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeFixed64(int fieldNumber, long value) throws IOException;

  /** Write an {@code sfixed64} field, including tag, to the stream. */
  public final void writeSFixed64(final int fieldNumber, final long value) throws IOException {
    writeFixed64(fieldNumber, value);
  }

  /** Write a {@code float} field, including tag, to the stream. */
  public final void writeFloat(final int fieldNumber, final float value) throws IOException {
    writeFixed32(fieldNumber, Float.floatToRawIntBits(value));
  }

  /** Write a {@code double} field, including tag, to the stream. */
  public final void writeDouble(final int fieldNumber, final double value) throws IOException {
    writeFixed64(fieldNumber, Double.doubleToRawLongBits(value));
  }

  /** Write a {@code bool} field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeBool(int fieldNumber, boolean value) throws IOException;

  /**
   * Write an enum field, including tag, to the stream. The provided value is the numeric value used
   * to represent the enum value on the wire (not the enum ordinal value).
   */
  public final void writeEnum(final int fieldNumber, final int value) throws IOException {
    writeInt32(fieldNumber, value);
  }

  /** Write a {@code string} field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeString(int fieldNumber, String value) throws IOException;

  /** Write a {@code bytes} field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeBytes(int fieldNumber, ByteString value) throws IOException;

  /** Write a {@code bytes} field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeByteArray(int fieldNumber, byte[] value) throws IOException;

  /** Write a {@code bytes} field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeByteArray(int fieldNumber, byte[] value, int offset, int length)
      throws IOException;

  /**
   * Write a {@code bytes} field, including tag, to the stream. This method will write all content
   * of the ByteBuffer regardless of the current position and limit (i.e., the number of bytes to be
   * written is value.capacity(), not value.remaining()). Furthermore, this method doesn't alter the
   * state of the passed-in ByteBuffer. Its position, limit, mark, etc. will remain unchanged. If
   * you only want to write the remaining bytes of a ByteBuffer, you can call {@code
   * writeByteBuffer(fieldNumber, byteBuffer.slice())}.
   */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeByteBuffer(int fieldNumber, ByteBuffer value) throws IOException;

  /** Write a single byte. */
  public final void writeRawByte(final byte value) throws IOException {
    write(value);
  }

  /** Write a single byte, represented by an integer value. */
  public final void writeRawByte(final int value) throws IOException {
    write((byte) value);
  }

  /** Write an array of bytes. */
  public final void writeRawBytes(final byte[] value) throws IOException {
    write(value, 0, value.length);
  }

  /** Write part of an array of bytes. */
  public final void writeRawBytes(final byte[] value, int offset, int length) throws IOException {
    write(value, offset, length);
  }

  /** Write a byte string. */
  public final void writeRawBytes(final ByteString value) throws IOException {
    value.writeTo(this);
  }

  /**
   * Write a ByteBuffer. This method will write all content of the ByteBuffer regardless of the
   * current position and limit (i.e., the number of bytes to be written is value.capacity(), not
   * value.remaining()). Furthermore, this method doesn't alter the state of the passed-in
   * ByteBuffer. Its position, limit, mark, etc. will remain unchanged. If you only want to write
   * the remaining bytes of a ByteBuffer, you can call {@code writeRawBytes(byteBuffer.slice())}.
   */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeRawBytes(final ByteBuffer value) throws IOException;

  /** Write an embedded message field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeMessage(final int fieldNumber, final MessageLite value)
      throws IOException;

  /** Write an embedded message field, including tag, to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  abstract void writeMessage(final int fieldNumber, final MessageLite value, Schema schema)
      throws IOException;

  /**
   * Write a MessageSet extension field to the stream. For historical reasons, the wire format
   * differs from normal fields.
   */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeMessageSetExtension(final int fieldNumber, final MessageLite value)
      throws IOException;

  /**
   * Write an unparsed MessageSet extension field to the stream. For historical reasons, the wire
   * format differs from normal fields.
   */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeRawMessageSetExtension(final int fieldNumber, final ByteString value)
      throws IOException;

  // -----------------------------------------------------------------

  /** Write an {@code int32} field to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeInt32NoTag(final int value) throws IOException;

  /** Write a {@code uint32} field to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeUInt32NoTag(int value) throws IOException;

  /** Write a {@code sint32} field to the stream. */
  public final void writeSInt32NoTag(final int value) throws IOException {
    writeUInt32NoTag(encodeZigZag32(value));
  }

  /** Write a {@code fixed32} field to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeFixed32NoTag(int value) throws IOException;

  /** Write a {@code sfixed32} field to the stream. */
  public final void writeSFixed32NoTag(final int value) throws IOException {
    writeFixed32NoTag(value);
  }

  /** Write an {@code int64} field to the stream. */
  public final void writeInt64NoTag(final long value) throws IOException {
    writeUInt64NoTag(value);
  }

  /** Write a {@code uint64} field to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeUInt64NoTag(long value) throws IOException;

  /** Write a {@code sint64} field to the stream. */
  public final void writeSInt64NoTag(final long value) throws IOException {
    writeUInt64NoTag(encodeZigZag64(value));
  }

  /** Write a {@code fixed64} field to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeFixed64NoTag(long value) throws IOException;

  /** Write a {@code sfixed64} field to the stream. */
  public final void writeSFixed64NoTag(final long value) throws IOException {
    writeFixed64NoTag(value);
  }

  /** Write a {@code float} field to the stream. */
  public final void writeFloatNoTag(final float value) throws IOException {
    writeFixed32NoTag(Float.floatToRawIntBits(value));
  }

  /** Write a {@code double} field to the stream. */
  public final void writeDoubleNoTag(final double value) throws IOException {
    writeFixed64NoTag(Double.doubleToRawLongBits(value));
  }

  /** Write a {@code bool} field to the stream. */
  public final void writeBoolNoTag(final boolean value) throws IOException {
    write((byte) (value ? 1 : 0));
  }

  /**
   * Write an enum field to the stream. The provided value is the numeric value used to represent
   * the enum value on the wire (not the enum ordinal value).
   */
  public final void writeEnumNoTag(final int value) throws IOException {
    writeInt32NoTag(value);
  }

  /** Write a {@code string} field to the stream. */
  // TODO: Document behavior on ill-formed UTF-16 input.
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeStringNoTag(String value) throws IOException;

  /** Write a {@code bytes} field to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeBytesNoTag(final ByteString value) throws IOException;

  /** Write a {@code bytes} field to the stream. */
  public final void writeByteArrayNoTag(final byte[] value) throws IOException {
    writeByteArrayNoTag(value, 0, value.length);
  }

  /** Write an embedded message field to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  public abstract void writeMessageNoTag(final MessageLite value) throws IOException;

  /** Write an embedded message field to the stream. */
  // Abstract to avoid overhead of additional virtual method calls.
  abstract void writeMessageNoTag(final MessageLite value, Schema schema) throws IOException;

  // =================================================================

  @ExperimentalApi
  @Override
  public abstract void write(byte value) throws IOException;

  @ExperimentalApi
  @Override
  public abstract void write(byte[] value, int offset, int length) throws IOException;

  @ExperimentalApi
  @Override
  public abstract void writeLazy(byte[] value, int offset, int length) throws IOException;

  @Override
  public abstract void write(ByteBuffer value) throws IOException;

  @ExperimentalApi
  @Override
  public abstract void writeLazy(ByteBuffer value) throws IOException;

  // =================================================================
  // =================================================================

  /**
   * Compute the number of bytes that would be needed to encode an {@code int32} field, including
   * tag.
   */
  public static int computeInt32Size(final int fieldNumber, final int value) {
    return computeTagSize(fieldNumber) + computeInt32SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code uint32} field, including
   * tag.
   */
  public static int computeUInt32Size(final int fieldNumber, final int value) {
    return computeTagSize(fieldNumber) + computeUInt32SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode an {@code sint32} field, including
   * tag.
   */
  public static int computeSInt32Size(final int fieldNumber, final int value) {
    return computeTagSize(fieldNumber) + computeSInt32SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code fixed32} field, including
   * tag.
   */
  public static int computeFixed32Size(final int fieldNumber, final int value) {
    return computeTagSize(fieldNumber) + computeFixed32SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode an {@code sfixed32} field, including
   * tag.
   */
  public static int computeSFixed32Size(final int fieldNumber, final int value) {
    return computeTagSize(fieldNumber) + computeSFixed32SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode an {@code int64} field, including
   * tag.
   */
  public static int computeInt64Size(final int fieldNumber, final long value) {
    return computeTagSize(fieldNumber) + computeInt64SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code uint64} field, including
   * tag.
   */
  public static int computeUInt64Size(final int fieldNumber, final long value) {
    return computeTagSize(fieldNumber) + computeUInt64SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode an {@code sint64} field, including
   * tag.
   */
  public static int computeSInt64Size(final int fieldNumber, final long value) {
    return computeTagSize(fieldNumber) + computeSInt64SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code fixed64} field, including
   * tag.
   */
  public static int computeFixed64Size(final int fieldNumber, final long value) {
    return computeTagSize(fieldNumber) + computeFixed64SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode an {@code sfixed64} field, including
   * tag.
   */
  public static int computeSFixed64Size(final int fieldNumber, final long value) {
    return computeTagSize(fieldNumber) + computeSFixed64SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code float} field, including
   * tag.
   */
  public static int computeFloatSize(final int fieldNumber, final float value) {
    return computeTagSize(fieldNumber) + computeFloatSizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code double} field, including
   * tag.
   */
  public static int computeDoubleSize(final int fieldNumber, final double value) {
    return computeTagSize(fieldNumber) + computeDoubleSizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code bool} field, including tag.
   */
  public static int computeBoolSize(final int fieldNumber, final boolean value) {
    return computeTagSize(fieldNumber) + computeBoolSizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode an enum field, including tag. The
   * provided value is the numeric value used to represent the enum value on the wire (not the enum
   * ordinal value).
   */
  public static int computeEnumSize(final int fieldNumber, final int value) {
    return computeTagSize(fieldNumber) + computeEnumSizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code string} field, including
   * tag.
   */
  public static int computeStringSize(final int fieldNumber, final String value) {
    return computeTagSize(fieldNumber) + computeStringSizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code bytes} field, including
   * tag.
   */
  public static int computeBytesSize(final int fieldNumber, final ByteString value) {
    return computeTagSize(fieldNumber) + computeBytesSizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code bytes} field, including
   * tag.
   */
  public static int computeByteArraySize(final int fieldNumber, final byte[] value) {
    return computeTagSize(fieldNumber) + computeByteArraySizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code bytes} field, including
   * tag.
   */
  public static int computeByteBufferSize(final int fieldNumber, final ByteBuffer value) {
    return computeTagSize(fieldNumber) + computeByteBufferSizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode an embedded message in lazy field,
   * including tag.
   */
  public static int computeLazyFieldSize(final int fieldNumber, final LazyFieldLite value) {
    return computeTagSize(fieldNumber) + computeLazyFieldSizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode an embedded message field, including
   * tag.
   */
  public static int computeMessageSize(final int fieldNumber, final MessageLite value) {
    return computeTagSize(fieldNumber) + computeMessageSizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode an embedded message field, including
   * tag.
   */
  static int computeMessageSize(
      final int fieldNumber, final MessageLite value, final Schema schema) {
    return computeTagSize(fieldNumber) + computeMessageSizeNoTag(value, schema);
  }

  /**
   * Compute the number of bytes that would be needed to encode a MessageSet extension to the
   * stream. For historical reasons, the wire format differs from normal fields.
   */
  public static int computeMessageSetExtensionSize(final int fieldNumber, final MessageLite value) {
    return computeTagSize(MESSAGE_SET_ITEM) * 2
        + computeUInt32Size(MESSAGE_SET_TYPE_ID, fieldNumber)
        + computeMessageSize(MESSAGE_SET_MESSAGE, value);
  }

  /**
   * Compute the number of bytes that would be needed to encode an unparsed MessageSet extension
   * field to the stream. For historical reasons, the wire format differs from normal fields.
   */
  public static int computeRawMessageSetExtensionSize(
      final int fieldNumber, final ByteString value) {
    return computeTagSize(MESSAGE_SET_ITEM) * 2
        + computeUInt32Size(MESSAGE_SET_TYPE_ID, fieldNumber)
        + computeBytesSize(MESSAGE_SET_MESSAGE, value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a lazily parsed MessageSet
   * extension field to the stream. For historical reasons, the wire format differs from normal
   * fields.
   */
  public static int computeLazyFieldMessageSetExtensionSize(
      final int fieldNumber, final LazyFieldLite value) {
    return computeTagSize(MESSAGE_SET_ITEM) * 2
        + computeUInt32Size(MESSAGE_SET_TYPE_ID, fieldNumber)
        + computeLazyFieldSize(MESSAGE_SET_MESSAGE, value);
  }

  // -----------------------------------------------------------------

  /** Compute the number of bytes that would be needed to encode a tag. */
  public static int computeTagSize(final int fieldNumber) {
    return computeUInt32SizeNoTag(makeTag(fieldNumber, 0));
  }

  static int makeTag(final int fieldNumber, final int wireType) {
    return (fieldNumber << TAG_TYPE_BITS) | wireType;
  }

  /**
   * Compute the number of bytes that would be needed to encode an {@code int32} field, including
   * tag.
   */
  public static int computeInt32SizeNoTag(final int value) {
    if (value >= 0) {
      return computeUInt32SizeNoTag(value);
    } else {
      // Must sign-extend.
      return MAX_VARINT_SIZE;
    }
  }

  /** Compute the number of bytes that would be needed to encode a {@code uint32} field. */
  public static int computeUInt32SizeNoTag(final int value) {
    if ((value & (~0 << 7)) == 0) {
      return 1;
    }
    if ((value & (~0 << 14)) == 0) {
      return 2;
    }
    if ((value & (~0 << 21)) == 0) {
      return 3;
    }
    if ((value & (~0 << 28)) == 0) {
      return 4;
    }
    return 5;
  }

  /** Compute the number of bytes that would be needed to encode an {@code sint32} field. */
  public static int computeSInt32SizeNoTag(final int value) {
    return computeUInt32SizeNoTag(encodeZigZag32(value));
  }

  /** Compute the number of bytes that would be needed to encode a {@code fixed32} field. */
  public static int computeFixed32SizeNoTag(@SuppressWarnings("unused") final int unused) {
    return FIXED32_SIZE;
  }

  /** Compute the number of bytes that would be needed to encode an {@code sfixed32} field. */
  public static int computeSFixed32SizeNoTag(@SuppressWarnings("unused") final int unused) {
    return FIXED32_SIZE;
  }

  /**
   * Compute the number of bytes that would be needed to encode an {@code int64} field, including
   * tag.
   */
  public static int computeInt64SizeNoTag(final long value) {
    return computeUInt64SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code uint64} field, including
   * tag.
   */
  public static int computeUInt64SizeNoTag(long value) {
    // handle two popular special cases up front ...
    if ((value & (~0L << 7)) == 0L) {
      return 1;
    }
    if (value < 0L) {
      return 10;
    }
    // ... leaving us with 8 remaining, which we can divide and conquer
    int n = 2;
    if ((value & (~0L << 35)) != 0L) {
      n += 4;
      value >>>= 28;
    }
    if ((value & (~0L << 21)) != 0L) {
      n += 2;
      value >>>= 14;
    }
    if ((value & (~0L << 14)) != 0L) {
      n += 1;
    }
    return n;
  }

  /** Compute the number of bytes that would be needed to encode an {@code sint64} field. */
  public static int computeSInt64SizeNoTag(final long value) {
    return computeUInt64SizeNoTag(encodeZigZag64(value));
  }

  /** Compute the number of bytes that would be needed to encode a {@code fixed64} field. */
  public static int computeFixed64SizeNoTag(@SuppressWarnings("unused") final long unused) {
    return FIXED64_SIZE;
  }

  /** Compute the number of bytes that would be needed to encode an {@code sfixed64} field. */
  public static int computeSFixed64SizeNoTag(@SuppressWarnings("unused") final long unused) {
    return FIXED64_SIZE;
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code float} field, including
   * tag.
   */
  public static int computeFloatSizeNoTag(@SuppressWarnings("unused") final float unused) {
    return FIXED32_SIZE;
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code double} field, including
   * tag.
   */
  public static int computeDoubleSizeNoTag(@SuppressWarnings("unused") final double unused) {
    return FIXED64_SIZE;
  }

  /** Compute the number of bytes that would be needed to encode a {@code bool} field. */
  public static int computeBoolSizeNoTag(@SuppressWarnings("unused") final boolean unused) {
    return 1;
  }

  /**
   * Compute the number of bytes that would be needed to encode an enum field. The provided value is
   * the numeric value used to represent the enum value on the wire (not the enum ordinal value).
   */
  public static int computeEnumSizeNoTag(final int value) {
    return computeInt32SizeNoTag(value);
  }

  /** Compute the number of bytes that would be needed to encode a {@code string} field. */
  public static int computeStringSizeNoTag(final String value) {
    int length;
    try {
      length = Utf8.encodedLength(value);
    } catch (UnpairedSurrogateException e) {
      // TODO: Consider using nio Charset methods instead.
      final byte[] bytes = value.getBytes(Internal.UTF_8);
      length = bytes.length;
    }

    return computeLengthDelimitedFieldSize(length);
  }

  /**
   * Compute the number of bytes that would be needed to encode an embedded message stored in lazy
   * field.
   */
  public static int computeLazyFieldSizeNoTag(final LazyFieldLite value) {
    return computeLengthDelimitedFieldSize(value.getSerializedSize());
  }

  /** Compute the number of bytes that would be needed to encode a {@code bytes} field. */
  public static int computeBytesSizeNoTag(final ByteString value) {
    return computeLengthDelimitedFieldSize(value.size());
  }

  /** Compute the number of bytes that would be needed to encode a {@code bytes} field. */
  public static int computeByteArraySizeNoTag(final byte[] value) {
    return computeLengthDelimitedFieldSize(value.length);
  }

  /** Compute the number of bytes that would be needed to encode a {@code bytes} field. */
  public static int computeByteBufferSizeNoTag(final ByteBuffer value) {
    return computeLengthDelimitedFieldSize(value.capacity());
  }

  /** Compute the number of bytes that would be needed to encode an embedded message field. */
  public static int computeMessageSizeNoTag(final MessageLite value) {
    return computeLengthDelimitedFieldSize(value.getSerializedSize());
  }

  /** Compute the number of bytes that would be needed to encode an embedded message field. */
  static int computeMessageSizeNoTag(final MessageLite value, final Schema schema) {
    return computeLengthDelimitedFieldSize(((AbstractMessageLite) value).getSerializedSize(schema));
  }

  static int computeLengthDelimitedFieldSize(int fieldLength) {
    return computeUInt32SizeNoTag(fieldLength) + fieldLength;
  }

  /**
   * Encode a ZigZag-encoded 32-bit value. ZigZag encodes signed integers into values that can be
   * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
   * to be varint encoded, thus always taking 10 bytes on the wire.)
   *
   * @param n A signed 32-bit integer.
   * @return An unsigned 32-bit integer, stored in a signed int because Java has no explicit
   *     unsigned support.
   */
  public static int encodeZigZag32(final int n) {
    // Note:  the right-shift must be arithmetic
    return (n << 1) ^ (n >> 31);
  }

  /**
   * Encode a ZigZag-encoded 64-bit value. ZigZag encodes signed integers into values that can be
   * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
   * to be varint encoded, thus always taking 10 bytes on the wire.)
   *
   * @param n A signed 64-bit integer.
   * @return An unsigned 64-bit integer, stored in a signed int because Java has no explicit
   *     unsigned support.
   */
  public static long encodeZigZag64(final long n) {
    // Note:  the right-shift must be arithmetic
    return (n << 1) ^ (n >> 63);
  }

  // =================================================================

  /**
   * Flushes the stream and forces any buffered bytes to be written. This does not flush the
   * underlying OutputStream.
   */
  public abstract void flush() throws IOException;

  /**
   * If writing to a flat array, return the space left in the array. Otherwise, throws {@code
   * UnsupportedOperationException}.
   */
  public abstract int spaceLeft();

  /**
   * Verifies that {@link #spaceLeft()} returns zero. It's common to create a byte array that is
   * exactly big enough to hold a message, then write to it with a {@code CodedOutputStream}.
   * Calling {@code checkNoSpaceLeft()} after writing verifies that the message was actually as big
   * as expected, which can help catch bugs.
   */
  public final void checkNoSpaceLeft() {
    if (spaceLeft() != 0) {
      throw new IllegalStateException("Did not write as much data as expected.");
    }
  }

  /**
   * If you create a CodedOutputStream around a simple flat array, you must not attempt to write
   * more bytes than the array has space. Otherwise, this exception will be thrown.
   */
  public static class OutOfSpaceException extends IOException {
    private static final long serialVersionUID = -6947486886997889499L;

    private static final String MESSAGE =
        "CodedOutputStream was writing to a flat byte array and ran out of space.";

    OutOfSpaceException() {
      super(MESSAGE);
    }

    OutOfSpaceException(String explanationMessage) {
      super(MESSAGE + ": " + explanationMessage);
    }

    OutOfSpaceException(Throwable cause) {
      super(MESSAGE, cause);
    }

    OutOfSpaceException(String explanationMessage, Throwable cause) {
      super(MESSAGE + ": " + explanationMessage, cause);
    }
  }

  /**
   * Get the total number of bytes successfully written to this stream. The returned value is not
   * guaranteed to be accurate if exceptions have been found in the middle of writing.
   */
  public abstract int getTotalBytesWritten();

  // =================================================================

  /** Write a {@code bytes} field to the stream. Visible for testing. */
  abstract void writeByteArrayNoTag(final byte[] value, final int offset, final int length)
      throws IOException;

  final void inefficientWriteStringNoTag(String value, UnpairedSurrogateException cause)
      throws IOException {
    logger.log(
        Level.WARNING,
        "Converting ill-formed UTF-16. Your Protocol Buffer will not round trip correctly!",
        cause);

    // Unfortunately there does not appear to be any way to tell Java to encode
    // UTF-8 directly into our buffer, so we have to let it create its own byte
    // array and then copy.
    // TODO: Consider using nio Charset methods instead.
    final byte[] bytes = value.getBytes(Internal.UTF_8);
    try {
      writeUInt32NoTag(bytes.length);
      writeLazy(bytes, 0, bytes.length);
    } catch (IndexOutOfBoundsException e) {
      throw new OutOfSpaceException(e);
    }
  }

  // =================================================================

  /**
   * Write a {@code group} field, including tag, to the stream.
   *
   * @deprecated groups are deprecated.
   */
  @Deprecated
  public final void writeGroup(final int fieldNumber, final MessageLite value) throws IOException {
    writeTag(fieldNumber, WIRETYPE_START_GROUP);
    writeGroupNoTag(value);
    writeTag(fieldNumber, WIRETYPE_END_GROUP);
  }

  /**
   * Write a {@code group} field, including tag, to the stream.
   *
   * @deprecated groups are deprecated.
   */
  @Deprecated
  final void writeGroup(final int fieldNumber, final MessageLite value, Schema schema)
      throws IOException {
    writeTag(fieldNumber, WIRETYPE_START_GROUP);
    writeGroupNoTag(value, schema);
    writeTag(fieldNumber, WIRETYPE_END_GROUP);
  }

  /**
   * Write a {@code group} field to the stream.
   *
   * @deprecated groups are deprecated.
   */
  @Deprecated
  public final void writeGroupNoTag(final MessageLite value) throws IOException {
    value.writeTo(this);
  }

  /**
   * Write a {@code group} field to the stream.
   *
   * @deprecated groups are deprecated.
   */
  @Deprecated
  final void writeGroupNoTag(final MessageLite value, Schema schema) throws IOException {
    schema.writeTo(value, wrapper);
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code group} field, including
   * tag.
   *
   * @deprecated groups are deprecated.
   */
  @Deprecated
  public static int computeGroupSize(final int fieldNumber, final MessageLite value) {
    return computeTagSize(fieldNumber) * 2 + value.getSerializedSize();
  }

  /**
   * Compute the number of bytes that would be needed to encode a {@code group} field, including
   * tag.
   *
   * @deprecated groups are deprecated.
   */
  @Deprecated
  static int computeGroupSize(final int fieldNumber, final MessageLite value, Schema schema) {
    return computeTagSize(fieldNumber) * 2 + computeGroupSizeNoTag(value, schema);
  }

  /** Compute the number of bytes that would be needed to encode a {@code group} field. */
  @Deprecated
  @InlineMe(replacement = "value.getSerializedSize()")
  public static int computeGroupSizeNoTag(final MessageLite value) {
    return value.getSerializedSize();
  }

  /** Compute the number of bytes that would be needed to encode a {@code group} field. */
  @Deprecated
  static int computeGroupSizeNoTag(final MessageLite value, Schema schema) {
    return ((AbstractMessageLite) value).getSerializedSize(schema);
  }

  /**
   * Encode and write a varint. {@code value} is treated as unsigned, so it won't be sign-extended
   * if negative.
   *
   * @deprecated use {@link #writeUInt32NoTag} instead.
   */
  @Deprecated
  @InlineMe(replacement = "this.writeUInt32NoTag(value)")
  public final void writeRawVarint32(int value) throws IOException {
    writeUInt32NoTag(value);
  }

  /**
   * Encode and write a varint.
   *
   * @deprecated use {@link #writeUInt64NoTag} instead.
   */
  @Deprecated
  @InlineMe(replacement = "this.writeUInt64NoTag(value)")
  public final void writeRawVarint64(long value) throws IOException {
    writeUInt64NoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a varint. {@code value} is treated
   * as unsigned, so it won't be sign-extended if negative.
   *
   * @deprecated use {@link #computeUInt32SizeNoTag(int)} instead.
   */
  @Deprecated
  @InlineMe(
      replacement = "CodedOutputStream.computeUInt32SizeNoTag(value)",
      imports = "com.google.protobuf.CodedOutputStream")
  public static int computeRawVarint32Size(final int value) {
    return computeUInt32SizeNoTag(value);
  }

  /**
   * Compute the number of bytes that would be needed to encode a varint.
   *
   * @deprecated use {@link #computeUInt64SizeNoTag(long)} instead.
   */
  @Deprecated
  @InlineMe(
      replacement = "CodedOutputStream.computeUInt64SizeNoTag(value)",
      imports = "com.google.protobuf.CodedOutputStream")
  public static int computeRawVarint64Size(long value) {
    return computeUInt64SizeNoTag(value);
  }

  /**
   * Write a little-endian 32-bit integer.
   *
   * @deprecated Use {@link #writeFixed32NoTag} instead.
   */
  @Deprecated
  @InlineMe(replacement = "this.writeFixed32NoTag(value)")
  public final void writeRawLittleEndian32(final int value) throws IOException {
    writeFixed32NoTag(value);
  }

  /**
   * Write a little-endian 64-bit integer.
   *
   * @deprecated Use {@link #writeFixed64NoTag} instead.
   */
  @Deprecated
  @InlineMe(replacement = "this.writeFixed64NoTag(value)")
  public final void writeRawLittleEndian64(final long value) throws IOException {
    writeFixed64NoTag(value);
  }

}
