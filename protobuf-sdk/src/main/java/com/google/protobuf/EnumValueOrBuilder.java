// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/protobuf/type.proto

// Protobuf Java Version: 3.25.5
package com.google.protobuf;

public interface EnumValueOrBuilder extends
    // @@protoc_insertion_point(interface_extends:google.protobuf.EnumValue)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Enum value name.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The name.
   */
  java.lang.String getName();
  /**
   * <pre>
   * Enum value name.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The bytes for name.
   */
  com.google.protobuf.ByteString
      getNameBytes();

  /**
   * <pre>
   * Enum value number.
   * </pre>
   *
   * <code>int32 number = 2;</code>
   * @return The number.
   */
  int getNumber();

  /**
   * <pre>
   * Protocol buffer options.
   * </pre>
   *
   * <code>repeated .google.protobuf.Option options = 3;</code>
   */
  java.util.List<com.google.protobuf.Option> 
      getOptionsList();
  /**
   * <pre>
   * Protocol buffer options.
   * </pre>
   *
   * <code>repeated .google.protobuf.Option options = 3;</code>
   */
  com.google.protobuf.Option getOptions(int index);
  /**
   * <pre>
   * Protocol buffer options.
   * </pre>
   *
   * <code>repeated .google.protobuf.Option options = 3;</code>
   */
  int getOptionsCount();
  /**
   * <pre>
   * Protocol buffer options.
   * </pre>
   *
   * <code>repeated .google.protobuf.Option options = 3;</code>
   */
  java.util.List<? extends com.google.protobuf.OptionOrBuilder> 
      getOptionsOrBuilderList();
  /**
   * <pre>
   * Protocol buffer options.
   * </pre>
   *
   * <code>repeated .google.protobuf.Option options = 3;</code>
   */
  com.google.protobuf.OptionOrBuilder getOptionsOrBuilder(
      int index);
}
