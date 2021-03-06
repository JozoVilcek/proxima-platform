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
import cz.o2.proxima.storage.Partition;
import cz.o2.proxima.storage.StreamElement;
import lombok.extern.slf4j.Slf4j;


/**
 * {@code BulkObserver} which is able to retry the observation on error.
 * The number of retries is configurable.
 */
@Stable
@Slf4j
public abstract class RetryableBulkObserver
    extends AbstractRetryableLogObserver
    implements BulkLogObserver {

  public RetryableBulkObserver(
      int maxRetries,
      String name,
      CommitLogReader commitLog) {

    super(maxRetries, name, commitLog);
  }

  @Override
  public final boolean onNext(
      StreamElement ingest, Partition partition,
      BulkLogObserver.OffsetCommitter confirm) {

    boolean ret = onNextInternal(ingest, partition, confirm);
    success();
    return ret;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected final ObserveHandle startInternal(Position position) {
    log.info(
        "Starting to process commitlog {} as {} from {}",
        getCommitLog().getUri(), getName(), getPosition());
    return getCommitLog().observeBulk(getName(), getPosition(), this);
  }

  /**
   * Called to observe the ingest data.
   * @param ingest the input data
   * @param context the callback to use to confirm processing
   * @return {@code true} to continue processing, {@code false} otherwise
   */
  protected boolean onNextInternal(
      StreamElement ingest,
      BulkLogObserver.OffsetCommitter context) {

    throw new UnsupportedOperationException(
        "Please override either of `onNextInternal` methods");
  }

  /**
   * Called to observe the ingest data.
   * @param ingest input data
   * @param partition source partition
   * @param context callback to use to confirm processing
   * @return {@code true} to continue processing, {@code false} otherwise
   */
  protected boolean onNextInternal(
      StreamElement ingest, Partition partition,
      BulkLogObserver.OffsetCommitter context) {

    return onNextInternal(ingest, context);
  }

}
