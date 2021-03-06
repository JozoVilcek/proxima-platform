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
package cz.o2.proxima.storage.kafka;

import cz.o2.proxima.storage.Partition;
import cz.o2.proxima.storage.commitlog.Offset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * Offset used in bulk consumption.
 */
class TopicOffset implements Offset {

  // map of partitionId -> committed offset
  private final int partition;
  @Getter
  private final long offset;

  TopicOffset(int partition, long offset) {
    this.partition = partition;
    this.offset = offset;
  }

  @Override
  public String toString() {
    return "TopicOffset(partition=" + partition + ", offset=" + offset + ")";
  }

  @Override
  public Partition getPartition() {
    return () -> partition;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TopicOffset) {
      TopicOffset other = (TopicOffset) obj;
      return other.partition == partition && other.offset == offset;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(partition, offset);
  }


  static List<TopicOffset> fromMap(Map<Integer, Long> offsetMap) {
    return offsetMap.entrySet().stream()
        .map(e -> new TopicOffset(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

}

