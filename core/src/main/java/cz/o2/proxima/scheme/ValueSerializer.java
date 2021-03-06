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
package cz.o2.proxima.scheme;

import cz.o2.proxima.annotations.Stable;
import java.io.Serializable;
import java.util.Optional;

/**
 * A serializer of values with specified scheme.
 */
@Stable
public interface ValueSerializer<T> extends Serializable {

  /**
   * Deserialize the bytes to materialized typed message.
   * If the deserialization fails the returned value is empty.
   * @param input the serialized data
   * @return optional deserialized output
   **/
  Optional<T> deserialize(byte[] input);

  /**
   * Serialize value to bytes.
   * @param value the deserialized value
   * @return serialized bytes
   */
  byte[] serialize(T value);


  /**
   * Retrieve a default value for the type.
   * @return default value of the type
   */
  T getDefault();


  /**
   * Check if given input is valid by trying to parse it.
   * @param input serialized data
   * @return {@code true} if this is valid byte representation
   */
  default boolean isValid(byte[] input) {
    return deserialize(input).isPresent();
  }

  /**
   * Retrieve raw java class type of this serializer.
   * @return the raw java class type
   */
  Class<T> getClassType();

}
