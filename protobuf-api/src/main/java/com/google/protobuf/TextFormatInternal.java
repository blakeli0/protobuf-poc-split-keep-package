package com.google.protobuf;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class TextFormatInternal {
    /**
     * Parse a 32-bit signed integer from the text. Unlike the Java standard {@code
     * Integer.parseInt()}, this function recognizes the prefixes "0x" and "0" to signify hexadecimal
     * and octal numbers, respectively.
     */
    static int parseInt32(final String text) throws NumberFormatException {
        return (int) parseInteger(text, true, false);
    }

    /**
     * Parse a 32-bit unsigned integer from the text. Unlike the Java standard {@code
     * Integer.parseInt()}, this function recognizes the prefixes "0x" and "0" to signify hexadecimal
     * and octal numbers, respectively. The result is coerced to a (signed) {@code int} when returned
     * since Java has no unsigned integer type.
     */
    static int parseUInt32(final String text) throws NumberFormatException {
        return (int) parseInteger(text, false, false);
    }

    /**
     * Parse a 64-bit signed integer from the text. Unlike the Java standard {@code
     * Integer.parseInt()}, this function recognizes the prefixes "0x" and "0" to signify hexadecimal
     * and octal numbers, respectively.
     */
    static long parseInt64(final String text) throws NumberFormatException {
        return parseInteger(text, true, true);
    }

    /**
     * Parse a 64-bit unsigned integer from the text. Unlike the Java standard {@code
     * Integer.parseInt()}, this function recognizes the prefixes "0x" and "0" to signify hexadecimal
     * and octal numbers, respectively. The result is coerced to a (signed) {@code long} when returned
     * since Java has no unsigned long type.
     */
    static long parseUInt64(final String text) throws NumberFormatException {
        return parseInteger(text, false, true);
    }

    private static long parseInteger(final String text, final boolean isSigned, final boolean isLong)
            throws NumberFormatException {
        int pos = 0;

        boolean negative = false;
        if (text.startsWith("-", pos)) {
            if (!isSigned) {
                throw new NumberFormatException("Number must be positive: " + text);
            }
            ++pos;
            negative = true;
        }

        int radix = 10;
        if (text.startsWith("0x", pos)) {
            pos += 2;
            radix = 16;
        } else if (text.startsWith("0", pos)) {
            radix = 8;
        }

        final String numberText = text.substring(pos);

        long result = 0;
        if (numberText.length() < 16) {
            // Can safely assume no overflow.
            result = Long.parseLong(numberText, radix);
            if (negative) {
                result = -result;
            }

            // Check bounds.
            // No need to check for 64-bit numbers since they'd have to be 16 chars
            // or longer to overflow.
            if (!isLong) {
                if (isSigned) {
                    if (result > Integer.MAX_VALUE || result < Integer.MIN_VALUE) {
                        throw new NumberFormatException(
                                "Number out of range for 32-bit signed integer: " + text);
                    }
                } else {
                    if (result >= (1L << 32) || result < 0) {
                        throw new NumberFormatException(
                                "Number out of range for 32-bit unsigned integer: " + text);
                    }
                }
            }
        } else {
            BigInteger bigValue = new BigInteger(numberText, radix);
            if (negative) {
                bigValue = bigValue.negate();
            }

            // Check bounds.
            if (!isLong) {
                if (isSigned) {
                    if (bigValue.bitLength() > 31) {
                        throw new NumberFormatException(
                                "Number out of range for 32-bit signed integer: " + text);
                    }
                } else {
                    if (bigValue.bitLength() > 32) {
                        throw new NumberFormatException(
                                "Number out of range for 32-bit unsigned integer: " + text);
                    }
                }
            } else {
                if (isSigned) {
                    if (bigValue.bitLength() > 63) {
                        throw new NumberFormatException(
                                "Number out of range for 64-bit signed integer: " + text);
                    }
                } else {
                    if (bigValue.bitLength() > 64) {
                        throw new NumberFormatException(
                                "Number out of range for 64-bit unsigned integer: " + text);
                    }
                }
            }

            result = bigValue.longValue();
        }

        return result;
    }

    /** Printer instance which escapes non-ASCII characters. */
    public static TextFormatInternal.Printer printer() {
        return TextFormatInternal.Printer.DEFAULT;
    }

    /** Helper class for converting protobufs to text. */
    public static final class Printer {

        // Printer instance which escapes non-ASCII characters.
        private static final TextFormatInternal.Printer DEFAULT = new TextFormatInternal.Printer(true, TypeRegistry.getEmptyTypeRegistry());

        /** Whether to escape non ASCII characters with backslash and octal. */
        private final boolean escapeNonAscii;

        private final TypeRegistry typeRegistry;

        private Printer(boolean escapeNonAscii, TypeRegistry typeRegistry) {
            this.escapeNonAscii = escapeNonAscii;
            this.typeRegistry = typeRegistry;
        }

        /**
         * Return a new Printer instance with the specified escape mode.
         *
         * @param escapeNonAscii If true, the new Printer will escape non-ASCII characters (this is the
         *     default behavior. If false, the new Printer will print non-ASCII characters as is. In
         *     either case, the new Printer still escapes newlines and quotes in strings.
         * @return a new Printer that clones all other configurations from the current {@link TextFormatInternal.Printer},
         *     with the escape mode set to the given parameter.
         */
        public TextFormatInternal.Printer escapingNonAscii(boolean escapeNonAscii) {
            return new TextFormatInternal.Printer(escapeNonAscii, typeRegistry);
        }

        /**
         * Creates a new {@link TextFormatInternal.Printer} using the given typeRegistry. The new Printer clones all other
         * configurations from the current {@link TextFormatInternal.Printer}.
         *
         * @throws IllegalArgumentException if a registry is already set.
         */
        public TextFormatInternal.Printer usingTypeRegistry(TypeRegistry typeRegistry) {
            if (this.typeRegistry != TypeRegistry.getEmptyTypeRegistry()) {
                throw new IllegalArgumentException("Only one typeRegistry is allowed.");
            }
            return new TextFormatInternal.Printer(escapeNonAscii, typeRegistry);
        }

        /**
         * Outputs a textual representation of the Protocol Message supplied into the parameter output.
         * (This representation is the new version of the classic "ProtocolPrinter" output from the
         * original Protocol Buffer system)
         */
        public void print(final MessageOrBuilder message, final Appendable output) throws IOException {
            print(message, multiLineOutput(output));
        }

        /** Outputs a textual representation of {@code fields} to {@code output}. */
        public void print(final UnknownFieldSet fields, final Appendable output) throws IOException {
            printUnknownFields(fields, multiLineOutput(output));
        }

        private void print(final MessageOrBuilder message, final TextFormatInternal.TextGenerator generator)
                throws IOException {
            if (message.getDescriptorForType().getFullName().equals("google.protobuf.Any")
                    && printAny(message, generator)) {
                return;
            }
            printMessage(message, generator);
        }

        /**
         * Attempt to print the 'google.protobuf.Any' message in a human-friendly format. Returns false
         * if the message isn't a valid 'google.protobuf.Any' message (in which case the message should
         * be rendered just like a regular message to help debugging).
         */
        private boolean printAny(final MessageOrBuilder message, final TextFormatInternal.TextGenerator generator)
                throws IOException {
            Descriptors.Descriptor messageType = message.getDescriptorForType();
            Descriptors.FieldDescriptor typeUrlField = messageType.findFieldByNumber(1);
            Descriptors.FieldDescriptor valueField = messageType.findFieldByNumber(2);
            if (typeUrlField == null
                    || typeUrlField.getType() != Descriptors.FieldDescriptor.Type.STRING
                    || valueField == null
                    || valueField.getType() != Descriptors.FieldDescriptor.Type.BYTES) {
                // The message may look like an Any but isn't actually an Any message (might happen if the
                // user tries to use DynamicMessage to construct an Any from incomplete Descriptor).
                return false;
            }
            String typeUrl = (String) message.getField(typeUrlField);
            // If type_url is not set, we will not be able to decode the content of the value, so just
            // print out the Any like a regular message.
            if (typeUrl.isEmpty()) {
                return false;
            }
            Object value = message.getField(valueField);

            Message.Builder contentBuilder = null;
            try {
                Descriptors.Descriptor contentType = typeRegistry.getDescriptorForTypeUrl(typeUrl);
                if (contentType == null) {
                    return false;
                }
                contentBuilder = DynamicMessage.getDefaultInstance(contentType).newBuilderForType();
                contentBuilder.mergeFrom((ByteString) value);
            } catch (InvalidProtocolBufferException e) {
                // The value of Any is malformed. We cannot print it out nicely, so fallback to printing out
                // the type_url and value as bytes. Note that we fail open here to be consistent with
                // text_format.cc, and also to allow a way for users to inspect the content of the broken
                // message.
                return false;
            }
            generator.print("[");
            generator.print(typeUrl);
            generator.print("] {");
            generator.eol();
            generator.indent();
            print(contentBuilder, generator);
            generator.outdent();
            generator.print("}");
            generator.eol();
            return true;
        }

        public String printFieldToString(final Descriptors.FieldDescriptor field, final Object value) {
            try {
                final StringBuilder text = new StringBuilder();
                printField(field, value, text);
                return text.toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public void printField(final Descriptors.FieldDescriptor field, final Object value, final Appendable output)
                throws IOException {
            printField(field, value, multiLineOutput(output));
        }

        private void printField(
                final Descriptors.FieldDescriptor field, final Object value, final TextFormatInternal.TextGenerator generator)
                throws IOException {
            // Sort map field entries by key
            if (field.isMapField()) {
                List<TextFormatInternal.Printer.MapEntryAdapter> adapters = new ArrayList<>();
                for (Object entry : (List<?>) value) {
                    adapters.add(new TextFormatInternal.Printer.MapEntryAdapter(entry, field));
                }
                Collections.sort(adapters);
                for (TextFormatInternal.Printer.MapEntryAdapter adapter : adapters) {
                    printSingleField(field, adapter.getEntry(), generator);
                }
            } else if (field.isRepeated()) {
                // Repeated field.  Print each element.
                for (Object element : (List<?>) value) {
                    printSingleField(field, element, generator);
                }
            } else {
                printSingleField(field, value, generator);
            }
        }

        /** An adapter class that can take a {@link MapEntry} and returns its key and entry. */
        private static class MapEntryAdapter implements Comparable<TextFormatInternal.Printer.MapEntryAdapter> {
            private Object entry;

            @SuppressWarnings({"rawtypes"})
            private MapEntry mapEntry;

            private final Descriptors.FieldDescriptor.JavaType fieldType;

            MapEntryAdapter(Object entry, Descriptors.FieldDescriptor fieldDescriptor) {
                if (entry instanceof MapEntry) {
                    this.mapEntry = (MapEntry) entry;
                } else {
                    this.entry = entry;
                }
                this.fieldType = extractFieldType(fieldDescriptor);
            }

            private static Descriptors.FieldDescriptor.JavaType extractFieldType(Descriptors.FieldDescriptor fieldDescriptor) {
                return fieldDescriptor.getMessageType().getFields().get(0).getJavaType();
            }

            Object getKey() {
                if (mapEntry != null) {
                    return mapEntry.getKey();
                }
                return null;
            }

            Object getEntry() {
                if (mapEntry != null) {
                    return mapEntry;
                }
                return entry;
            }

            @Override
            public int compareTo(TextFormatInternal.Printer.MapEntryAdapter b) {
                if (getKey() == null || b.getKey() == null) {
//                    logger.info("Invalid key for map field.");
                    return -1;
                }
                switch (fieldType) {
                    case BOOLEAN:
                        return Boolean.valueOf((boolean) getKey()).compareTo((boolean) b.getKey());
                    case LONG:
                        return Long.valueOf((long) getKey()).compareTo((long) b.getKey());
                    case INT:
                        return Integer.valueOf((int) getKey()).compareTo((int) b.getKey());
                    case STRING:
                        String aString = (String) getKey();
                        String bString = (String) b.getKey();
                        if (aString == null && bString == null) {
                            return 0;
                        } else if (aString == null && bString != null) {
                            return -1;
                        } else if (aString != null && bString == null) {
                            return 1;
                        } else {
                            return aString.compareTo(bString);
                        }
                    default:
                        return 0;
                }
            }
        }

        /**
         * Outputs a textual representation of the value of given field value.
         *
         * @param field the descriptor of the field
         * @param value the value of the field
         * @param output the output to which to append the formatted value
         * @throws ClassCastException if the value is not appropriate for the given field descriptor
         * @throws IOException if there is an exception writing to the output
         */
        public void printFieldValue(
                final Descriptors.FieldDescriptor field, final Object value, final Appendable output)
                throws IOException {
            printFieldValue(field, value, multiLineOutput(output));
        }

        private void printFieldValue(
                final Descriptors.FieldDescriptor field, final Object value, final TextFormatInternal.TextGenerator generator)
                throws IOException {
            switch (field.getType()) {
                case INT32:
                case SINT32:
                case SFIXED32:
                    generator.print(((Integer) value).toString());
                    break;

                case INT64:
                case SINT64:
                case SFIXED64:
                    generator.print(((Long) value).toString());
                    break;

                case BOOL:
                    generator.print(((Boolean) value).toString());
                    break;

                case FLOAT:
                    generator.print(((Float) value).toString());
                    break;

                case DOUBLE:
                    generator.print(((Double) value).toString());
                    break;

                case UINT32:
                case FIXED32:
                    generator.print(unsignedToString((Integer) value));
                    break;

                case UINT64:
                case FIXED64:
                    generator.print(unsignedToString((Long) value));
                    break;

                case STRING:
                    generator.print("\"");
                    generator.print(
                            escapeNonAscii
                                    ? TextFormatEscaper.escapeText((String) value)
                                    : escapeDoubleQuotesAndBackslashes((String) value).replace("\n", "\\n"));
                    generator.print("\"");
                    break;

                case BYTES:
                    generator.print("\"");
                    if (value instanceof ByteString) {
                        generator.print(escapeBytes((ByteString) value));
                    } else {
                        generator.print(escapeBytes((byte[]) value));
                    }
                    generator.print("\"");
                    break;

                case ENUM:
                    generator.print(((Descriptors.EnumValueDescriptor) value).getName());
                    break;

                case MESSAGE:
                case GROUP:
                    print((MessageOrBuilder) value, generator);
                    break;
            }
        }

        /** Like {@code print()}, but writes directly to a {@code String} and returns it. */
        public String printToString(final MessageOrBuilder message) {
            try {
                final StringBuilder text = new StringBuilder();
                print(message, text);
                return text.toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        /** Like {@code print()}, but writes directly to a {@code String} and returns it. */
        public String printToString(final UnknownFieldSet fields) {
            try {
                final StringBuilder text = new StringBuilder();
                print(fields, text);
                return text.toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * Generates a human readable form of this message, useful for debugging and other purposes,
         * with no newline characters.
         */
        public String shortDebugString(final MessageOrBuilder message) {
            try {
                final StringBuilder text = new StringBuilder();
                print(message, singleLineOutput(text));
                return text.toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * Generates a human readable form of the field, useful for debugging and other purposes, with
         * no newline characters.
         */
        public String shortDebugString(final Descriptors.FieldDescriptor field, final Object value) {
            try {
                final StringBuilder text = new StringBuilder();
                printField(field, value, singleLineOutput(text));
                return text.toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * Generates a human readable form of the unknown fields, useful for debugging and other
         * purposes, with no newline characters.
         */
        public String shortDebugString(final UnknownFieldSet fields) {
            try {
                final StringBuilder text = new StringBuilder();
                printUnknownFields(fields, singleLineOutput(text));
                return text.toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static void printUnknownFieldValue(
                final int tag, final Object value, final TextFormatInternal.TextGenerator generator) throws IOException {
            switch (WireFormat.getTagWireType(tag)) {
                case WireFormat.WIRETYPE_VARINT:
                    generator.print(unsignedToString((Long) value));
                    break;
                case WireFormat.WIRETYPE_FIXED32:
                    generator.print(String.format((Locale) null, "0x%08x", (Integer) value));
                    break;
                case WireFormat.WIRETYPE_FIXED64:
                    generator.print(String.format((Locale) null, "0x%016x", (Long) value));
                    break;
                case WireFormat.WIRETYPE_LENGTH_DELIMITED:
                    try {
                        // Try to parse and print the field as an embedded message
                        UnknownFieldSet message = UnknownFieldSet.parseFrom((ByteString) value);
                        generator.print("{");
                        generator.eol();
                        generator.indent();
                        printUnknownFields(message, generator);
                        generator.outdent();
                        generator.print("}");
                    } catch (InvalidProtocolBufferException e) {
                        // If not parseable as a message, print as a String
                        generator.print("\"");
                        generator.print(escapeBytes((ByteString) value));
                        generator.print("\"");
                    }
                    break;
                case WireFormat.WIRETYPE_START_GROUP:
                    printUnknownFields((UnknownFieldSet) value, generator);
                    break;
                default:
                    throw new IllegalArgumentException("Bad tag: " + tag);
            }
        }

        private void printMessage(final MessageOrBuilder message, final TextFormatInternal.TextGenerator generator)
                throws IOException {
            for (Map.Entry<Descriptors.FieldDescriptor, Object> field : message.getAllFields().entrySet()) {
                printField(field.getKey(), field.getValue(), generator);
            }
            printUnknownFields(message.getUnknownFields(), generator);
        }

        private void printSingleField(
                final Descriptors.FieldDescriptor field, final Object value, final TextFormatInternal.TextGenerator generator)
                throws IOException {
            if (field.isExtension()) {
                generator.print("[");
                // We special-case MessageSet elements for compatibility with proto1.
                if (field.getContainingType().getOptions().getMessageSetWireFormat()
                        && (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE)
                        && (field.isOptional())
                        // object equality
                        && (field.getExtensionScope() == field.getMessageType())) {
                    generator.print(field.getMessageType().getFullName());
                } else {
                    generator.print(field.getFullName());
                }
                generator.print("]");
            } else {
                if (field.getType() == Descriptors.FieldDescriptor.Type.GROUP) {
                    // Groups must be serialized with their original capitalization.
                    generator.print(field.getMessageType().getName());
                } else {
                    generator.print(field.getName());
                }
            }

            if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                generator.print(" {");
                generator.eol();
                generator.indent();
            } else {
                generator.print(": ");
            }

            printFieldValue(field, value, generator);

            if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                generator.outdent();
                generator.print("}");
            }
            generator.eol();
        }

        private static void printUnknownFields(
                final UnknownFieldSet unknownFields, final TextFormatInternal.TextGenerator generator) throws IOException {
            for (Map.Entry<Integer, UnknownFieldSet.Field> entry : unknownFields.asMap().entrySet()) {
                final int number = entry.getKey();
                final UnknownFieldSet.Field field = entry.getValue();
                printUnknownField(number, WireFormat.WIRETYPE_VARINT, field.getVarintList(), generator);
                printUnknownField(number, WireFormat.WIRETYPE_FIXED32, field.getFixed32List(), generator);
                printUnknownField(number, WireFormat.WIRETYPE_FIXED64, field.getFixed64List(), generator);
                printUnknownField(
                        number,
                        WireFormat.WIRETYPE_LENGTH_DELIMITED,
                        field.getLengthDelimitedList(),
                        generator);
                for (final UnknownFieldSet value : field.getGroupList()) {
                    generator.print(entry.getKey().toString());
                    generator.print(" {");
                    generator.eol();
                    generator.indent();
                    printUnknownFields(value, generator);
                    generator.outdent();
                    generator.print("}");
                    generator.eol();
                }
            }
        }

        private static void printUnknownField(
                final int number, final int wireType, final List<?> values, final TextFormatInternal.TextGenerator generator)
                throws IOException {
            for (final Object value : values) {
                generator.print(String.valueOf(number));
                generator.print(": ");
                printUnknownFieldValue(wireType, value, generator);
                generator.eol();
            }
        }
    }

    /** Convert an unsigned 32-bit integer to a string. */
    public static String unsignedToString(final int value) {
        if (value >= 0) {
            return Integer.toString(value);
        } else {
            return Long.toString(value & 0x00000000FFFFFFFFL);
        }
    }

    /** Convert an unsigned 64-bit integer to a string. */
    public static String unsignedToString(final long value) {
        if (value >= 0) {
            return Long.toString(value);
        } else {
            // Pull off the most-significant bit so that BigInteger doesn't think
            // the number is negative, then set it again using setBit().
            return BigInteger.valueOf(value & 0x7FFFFFFFFFFFFFFFL).setBit(63).toString();
        }
    }

    private static TextFormatInternal.TextGenerator multiLineOutput(Appendable output) {
        return new TextFormatInternal.TextGenerator(output, false);
    }

    private static TextFormatInternal.TextGenerator singleLineOutput(Appendable output) {
        return new TextFormatInternal.TextGenerator(output, true);
    }

    private static String escapeBytes(ByteString input) {
        return TextFormatEscaper.escapeBytes(input);
    }

    public static String escapeBytes(byte[] input) {
        return TextFormatEscaper.escapeBytes(input);
    }

    public static String escapeDoubleQuotesAndBackslashes(final String input) {
        return TextFormatEscaper.escapeDoubleQuotesAndBackslashes(input);
    }

    /** An inner class for writing text to the output stream. */
    private static final class TextGenerator {
        private final Appendable output;
        private final StringBuilder indent = new StringBuilder();
        private final boolean singleLineMode;
        // While technically we are "at the start of a line" at the very beginning of the output, all
        // we would do in response to this is emit the (zero length) indentation, so it has no effect.
        // Setting it false here does however suppress an unwanted leading space in single-line mode.
        private boolean atStartOfLine = false;

        private TextGenerator(final Appendable output, boolean singleLineMode) {
            this.output = output;
            this.singleLineMode = singleLineMode;
        }

        /**
         * Indent text by two spaces. After calling Indent(), two spaces will be inserted at the
         * beginning of each line of text. Indent() may be called multiple times to produce deeper
         * indents.
         */
        public void indent() {
            indent.append("  ");
        }

        /** Reduces the current indent level by two spaces, or crashes if the indent level is zero. */
        public void outdent() {
            final int length = indent.length();
            if (length == 0) {
                throw new IllegalArgumentException(" Outdent() without matching Indent().");
            }
            indent.setLength(length - 2);
        }

        /**
         * Print text to the output stream. Bare newlines are never expected to be passed to this
         * method; to indicate the end of a line, call "eol()".
         */
        public void print(final CharSequence text) throws IOException {
            if (atStartOfLine) {
                atStartOfLine = false;
                output.append(singleLineMode ? " " : indent);
            }
            output.append(text);
        }

        /**
         * Signifies reaching the "end of the current line" in the output. In single-line mode, this
         * does not result in a newline being emitted, but ensures that a separating space is written
         * before the next output.
         */
        public void eol() throws IOException {
            if (!singleLineMode) {
                output.append("\n");
            }
            atStartOfLine = true;
        }
    }
}
