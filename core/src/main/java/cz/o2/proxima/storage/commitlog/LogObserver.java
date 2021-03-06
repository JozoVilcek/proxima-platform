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

import cz.o2.proxima.annotations.Stable;
import cz.o2.proxima.storage.StreamElement;
import java.io.Serializable;
import javax.annotation.Nullable;

/**
 * Observer of the data in the commit log.
 * This observer provides access only to the data that was not yet consumed
 * by the named consumer. The consumer maintains position in the commit log
 * so if it crashes, then it will continue consuming from the committed position.
 * However, it is not guaranteed that a message will not be delivered multiple
 * times, nor it is guaranteed that messages will be delivered in-order.
 *
 * The implementation has to override either of `onNext` methods.
 */
@Stable
public interface LogObserver extends LogObserverBase {

  /**
   * Committer for manipulation with offset during consumption.
   */
  interface OffsetCommitter extends Serializable {

    /**
     * Confirm processing of element.
     * @param success success/fail flag
     * @param error error that was thrown during processing
     */
    void commit(boolean success, @Nullable Throwable error);

    /**
     * Successfully confirm the processing.
     */
    default void confirm() {
      commit(true, null);
    }

    /**
     * Confirm failure of processing.
     * @param error error caught during processing
     */
    default void fail(Throwable error) {
      commit(false, error);
    }

    /**
     * Nack processing (no error thrown, message can be immediately retried).
     */
    default void nack() {
      commit(false, null);
    }

  }

  /**
   * Process next record in the commit log.
   * @param ingest the ingested data written to the commit log
   * @param committer a context that the application must use to confirm processing
   * of the ingest. If the application fails to do so, the result is undefined
   * @return {@code true} if the processing should continue, {@code false} otherwise
   **/
  boolean onNext(StreamElement ingest, OffsetCommitter committer);

}
