// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

package com.google.protobuf;

import java.io.IOException;

/**
 * Provide text format escaping of proto instances. These ASCII characters are escaped:
 *
 * ASCII #7   (bell) --> \a
 * ASCII #8   (backspace) --> \b
 * ASCII #9   (horizontal tab) --> \t
 * ASCII #10  (linefeed) --> \n
 * ASCII #11  (vertical tab) --> \v
 * ASCII #13  (carriage return) --> \r
 * ASCII #12  (formfeed) --> \f
 * ASCII #34  (apostrophe) --> \'
 * ASCII #39  (straight double quote) --> \"
 * ASCII #92  (backslash) --> \\
 *
 * Other printable ASCII characters between 32 and 127 inclusive are output as is, unescaped.
 * Other ASCII characters less than 32 and all Unicode characters 128 or greater are
 * first encoded as UTF-8, then each byte is escaped individually as a 3-digit octal escape.
 */
final class TextFormatEscaper {
  private TextFormatEscaper() {}

  private interface ByteSequence {
    int size();

    byte byteAt(int offset);
  }

  /**
   * Backslash escapes bytes in the format used in protocol buffer text format.
   */
  static String escapeBytes(ByteSequence input) {
    final StringBuilder builder = new StringBuilder(input.size());
    for (int i = 0; i < input.size(); i++) {
      byte b = input.byteAt(i);
      switch (b) {
        case 0x07:
          builder.append("\\a");
          break;
        case '\b':
          builder.append("\\b");
          break;
        case '\f':
          builder.append("\\f");
          break;
        case '\n':
          builder.append("\\n");
          break;
        case '\r':
          builder.append("\\r");
          break;
        case '\t':
          builder.append("\\t");
          break;
        case 0x0b:
          builder.append("\\v");
          break;
        case '\\':
          builder.append("\\\\");
          break;
        case '\'':
          builder.append("\\\'");
          break;
        case '"':
          builder.append("\\\"");
          break;
        default:
          // Only ASCII characters between 0x20 (space) and 0x7e (tilde) are
          // printable.  Other byte values must be escaped.
          if (b >= 0x20 && b <= 0x7e) {
            builder.append((char) b);
          } else {
            builder.append('\\');
            builder.append((char) ('0' + ((b >>> 6) & 3)));
            builder.append((char) ('0' + ((b >>> 3) & 7)));
            builder.append((char) ('0' + (b & 7)));
          }
          break;
      }
    }
    return builder.toString();
  }

  /**
   * Backslash escapes bytes in the format used in protocol buffer text format.
   */
  static String escapeBytes(final ByteString input) {
    return escapeBytes(
        new ByteSequence() {
          @Override
          public int size() {
            return input.size();
          }

          @Override
          public byte byteAt(int offset) {
            return input.byteAt(offset);
          }
        });
  }

  /** Like {@link #escapeBytes(ByteString)}, but used for byte array. */
  static String escapeBytes(final byte[] input) {
    return escapeBytes(
        new ByteSequence() {
          @Override
          public int size() {
            return input.length;
          }

          @Override
          public byte byteAt(int offset) {
            return input[offset];
          }
        });
  }

  /**
   * Like {@link #escapeBytes(ByteString)}, but escapes a text string.
   */
  static String escapeText(String input) {
    return escapeBytes(ByteString.copyFromUtf8(input));
  }

  /** Escape double quotes and backslashes in a String for unicode output of a message. */
  static String escapeDoubleQuotesAndBackslashes(String input) {
    return input.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /** Is this an octal digit? */
  private static boolean isOctal(final byte c) {
    return '0' <= c && c <= '7';
  }

  /** Is this a hex digit? */
  private static boolean isHex(final byte c) {
    return ('0' <= c && c <= '9') || ('a' <= c && c <= 'f') || ('A' <= c && c <= 'F');
  }

  /**
   * Interpret a character as a digit (in any base up to 36) and return the numeric value. This is
   * like {@code Character.digit()} but we don't accept non-ASCII digits.
   */
  private static int digitValue(final byte c) {
    if ('0' <= c && c <= '9') {
      return c - '0';
    } else if ('a' <= c && c <= 'z') {
      return c - 'a' + 10;
    } else {
      return c - 'A' + 10;
    }
  }

  /**
   * Thrown by {@link TextFormat#unescapeBytes} and {@link TextFormat#unescapeText} when an invalid
   * escape sequence is seen.
   */
  static class InvalidEscapeSequenceException extends IOException {
    private static final long serialVersionUID = -8164033650142593305L;

    InvalidEscapeSequenceException(final String description) {
      super(description);
    }
  }

  static ByteString unescapeBytes(CharSequence charString)
          throws TextFormatEscaper.InvalidEscapeSequenceException {
    // First convert the Java character sequence to UTF-8 bytes.
    ByteString input = ByteString.copyFromUtf8(charString.toString());
    // Then unescape certain byte sequences introduced by ASCII '\\'.  The valid
    // escapes can all be expressed with ASCII characters, so it is safe to
    // operate on bytes here.
    //
    // Unescaping the input byte array will result in a byte sequence that's no
    // longer than the input.  That's because each escape sequence is between
    // two and four bytes long and stands for a single byte.
    final byte[] result = new byte[input.size()];
    int pos = 0;
    for (int i = 0; i < input.size(); i++) {
      byte c = input.byteAt(i);
      if (c == '\\') {
        if (i + 1 < input.size()) {
          ++i;
          c = input.byteAt(i);
          if (isOctal(c)) {
            // Octal escape.
            int code = digitValue(c);
            if (i + 1 < input.size() && isOctal(input.byteAt(i + 1))) {
              ++i;
              code = code * 8 + digitValue(input.byteAt(i));
            }
            if (i + 1 < input.size() && isOctal(input.byteAt(i + 1))) {
              ++i;
              code = code * 8 + digitValue(input.byteAt(i));
            }
            // TODO: Check that 0 <= code && code <= 0xFF.
            result[pos++] = (byte) code;
          } else {
            switch (c) {
              case 'a':
                result[pos++] = 0x07;
                break;
              case 'b':
                result[pos++] = '\b';
                break;
              case 'f':
                result[pos++] = '\f';
                break;
              case 'n':
                result[pos++] = '\n';
                break;
              case 'r':
                result[pos++] = '\r';
                break;
              case 't':
                result[pos++] = '\t';
                break;
              case 'v':
                result[pos++] = 0x0b;
                break;
              case '\\':
                result[pos++] = '\\';
                break;
              case '\'':
                result[pos++] = '\'';
                break;
              case '"':
                result[pos++] = '\"';
                break;
              case '?':
                result[pos++] = '?';
                break;

              case 'x':
                // hex escape
                int code = 0;
                if (i + 1 < input.size() && isHex(input.byteAt(i + 1))) {
                  ++i;
                  code = digitValue(input.byteAt(i));
                } else {
                  throw new TextFormatEscaper.InvalidEscapeSequenceException(
                          "Invalid escape sequence: '\\x' with no digits");
                }
                if (i + 1 < input.size() && isHex(input.byteAt(i + 1))) {
                  ++i;
                  code = code * 16 + digitValue(input.byteAt(i));
                }
                result[pos++] = (byte) code;
                break;

              case 'u':
                // Unicode escape
                ++i;
                if (i + 3 < input.size()
                        && isHex(input.byteAt(i))
                        && isHex(input.byteAt(i + 1))
                        && isHex(input.byteAt(i + 2))
                        && isHex(input.byteAt(i + 3))) {
                  char ch =
                          (char)
                                  (digitValue(input.byteAt(i)) << 12
                                          | digitValue(input.byteAt(i + 1)) << 8
                                          | digitValue(input.byteAt(i + 2)) << 4
                                          | digitValue(input.byteAt(i + 3)));

                  if (ch >= Character.MIN_SURROGATE && ch <= Character.MAX_SURROGATE) {
                    throw new TextFormatEscaper.InvalidEscapeSequenceException(
                            "Invalid escape sequence: '\\u' refers to a surrogate");
                  }
                  byte[] chUtf8 = Character.toString(ch).getBytes(Internal.UTF_8);
                  System.arraycopy(chUtf8, 0, result, pos, chUtf8.length);
                  pos += chUtf8.length;
                  i += 3;
                } else {
                  throw new TextFormatEscaper.InvalidEscapeSequenceException(
                          "Invalid escape sequence: '\\u' with too few hex chars");
                }
                break;

              case 'U':
                // Unicode escape
                ++i;
                if (i + 7 >= input.size()) {
                  throw new TextFormatEscaper.InvalidEscapeSequenceException(
                          "Invalid escape sequence: '\\U' with too few hex chars");
                }
                int codepoint = 0;
                for (int offset = i; offset < i + 8; offset++) {
                  byte b = input.byteAt(offset);
                  if (!isHex(b)) {
                    throw new TextFormatEscaper.InvalidEscapeSequenceException(
                            "Invalid escape sequence: '\\U' with too few hex chars");
                  }
                  codepoint = (codepoint << 4) | digitValue(b);
                }
                if (!Character.isValidCodePoint(codepoint)) {
                  throw new TextFormatEscaper.InvalidEscapeSequenceException(
                          "Invalid escape sequence: '\\U"
                                  + input.substring(i, i + 8).toStringUtf8()
                                  + "' is not a valid code point value");
                }
                Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.of(codepoint);
                if (unicodeBlock != null
                        && (unicodeBlock.equals(Character.UnicodeBlock.LOW_SURROGATES)
                        || unicodeBlock.equals(Character.UnicodeBlock.HIGH_SURROGATES)
                        || unicodeBlock.equals(
                        Character.UnicodeBlock.HIGH_PRIVATE_USE_SURROGATES))) {
                  throw new TextFormatEscaper.InvalidEscapeSequenceException(
                          "Invalid escape sequence: '\\U"
                                  + input.substring(i, i + 8).toStringUtf8()
                                  + "' refers to a surrogate code unit");
                }
                int[] codepoints = new int[1];
                codepoints[0] = codepoint;
                byte[] chUtf8 = new String(codepoints, 0, 1).getBytes(Internal.UTF_8);
                System.arraycopy(chUtf8, 0, result, pos, chUtf8.length);
                pos += chUtf8.length;
                i += 7;
                break;

              default:
                throw new TextFormatEscaper.InvalidEscapeSequenceException(
                        "Invalid escape sequence: '\\" + (char) c + '\'');
            }
          }
        } else {
          throw new TextFormatEscaper.InvalidEscapeSequenceException(
                  "Invalid escape sequence: '\\' at end of string.");
        }
      } else {
        result[pos++] = c;
      }
    }

    return result.length == pos
            ? ByteString.wrap(result) // This reference has not been out of our control.
            : ByteString.copyFrom(result, 0, pos);
  }
}
