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
package cz.o2.proxima.storage.commitlog;

import cz.o2.proxima.storage.StreamElement;
import cz.o2.proxima.storage.commitlog.Partitioner;

/**
 * A partitioner based on key of entity.
 * This is the default partitioner.
 */
public class KeyPartitioner implements Partitioner {

  @Override
  public int getPartitionId(StreamElement element) {
    return element.getKey().hashCode();
  }

}
