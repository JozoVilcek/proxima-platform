/**
 * Copyright 2017-2018 O2 Czech Republic, a.s.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cz.o2.proxima.scheme.proto;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import cz.o2.proxima.scheme.ValueSerializer;
import cz.o2.proxima.scheme.ValueSerializerFactory;
import cz.o2.proxima.storage.UriUtil;
import cz.o2.proxima.util.Classpath;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheme factory for `json-proto` scheme, which transforms json data
 * to same-scheme protobuf.
 * Fields from json not found in protobuf specification are ignored.
 */
@Slf4j
public class JsonProtoSerializerFactory implements ValueSerializerFactory {

  private static final Charset CHARSET = Charset.forName("UTF-8");
  private final Map<URI, ValueSerializer<?>> serializers = new HashMap<>();

  @Override
  public String getAcceptableScheme() {
    return "json-proto";
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> ValueSerializer<T> getValueSerializer(URI specifier) {
    return (ValueSerializer) serializers.computeIfAbsent(
        specifier, this::createSerializer);
  }

  @SuppressWarnings("unchecked")
  private ValueSerializer createSerializer(URI uri) {

    return new ValueSerializer() {

      final String protoClass = uri.getSchemeSpecificPart();
      final Class clz = Classpath.findClass(protoClass, AbstractMessage.class);
      final JsonProtoSerializerFactory factory = JsonProtoSerializerFactory.this;
      final boolean strictScheme = Optional
          .ofNullable(UriUtil.parseQuery(uri).get("strictScheme"))
          .map(Boolean::valueOf)
          .orElse(false);
      @Nullable
      transient AbstractMessage defVal = null;
      @Nullable
      transient Method builder = null;
      @Nullable
      transient JsonFormat.Parser parser = null;

      @Override
      public Optional deserialize(byte[] input) {
        try {
          AbstractMessage.Builder newBuilder = newBuilder(builder());
          if (input.length == 0) {
            input = new byte[] {'{', '}'};
          }
          parser().merge(new String(input, CHARSET), newBuilder);
          return Optional.of(newBuilder.build());
        } catch (InvalidProtocolBufferException ex) {
          log.warn("Failed to parse input {}", new String(input), ex);
          return Optional.empty();
        }
      }

      @SuppressWarnings("unchecked")
      @Override
      public byte[] serialize(Object value) {
        try {
          StringBuilder sb = new StringBuilder();
          JsonFormat.printer().appendTo((MessageOrBuilder) value, sb);
          return sb.toString().getBytes(CHARSET);
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }

      @Override
      public Object getDefault() {
        if (defVal == null) {
          defVal = ProtoSerializerFactory.getDefaultInstance(protoClass);
        }
        return defVal;
      }

      private synchronized Method builder() {
        if (builder == null) {
          builder = getBuilder(protoClass);
        }
        return builder;
      }

      private synchronized JsonFormat.Parser parser() {
        if (parser == null) {
          parser = strictScheme
              ? JsonFormat.parser()
              : JsonFormat.parser().ignoringUnknownFields();
        }
        return parser;
      }

      @Override
      public Class getClassType() {
        return clz;
      }

    };
  }

  @SuppressWarnings("unchecked")
  static Method getBuilder(String protoClass) {
    try {
      Class<GeneratedMessage> cls = Classpath.findClass(
          protoClass, GeneratedMessage.class);
      return cls.getMethod("newBuilder");
    } catch (Exception ex) {
      throw new IllegalArgumentException(
          "Cannot retrieve builder for type " + protoClass);
    }
  }

  static AbstractMessage.Builder newBuilder(Method newBuilder) {
    try {
      return (AbstractMessage.Builder) newBuilder.invoke(null);
    } catch (IllegalAccessException | IllegalArgumentException
        | InvocationTargetException ex) {

      throw new RuntimeException(ex);
    }
  }

}
