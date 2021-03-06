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
package cz.o2.proxima.storage.hbase;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import cz.o2.proxima.storage.UriUtil;
import java.net.URI;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;

/**
 * Various utils.
 */
@Slf4j
class Util {

  private static final String FAMILY_QUERY = "family";

  static Configuration getConf(URI uri) {
    Configuration conf = HBaseConfiguration.create();
    conf.set(HConstants.ZOOKEEPER_QUORUM, uri.getAuthority());
    return conf;
  }

  static String getTable(URI uri) {
    String table = uri.getPath().substring(1);
    Preconditions.checkArgument(
        !Strings.isNullOrEmpty(table), "Table cannot be empty in " + uri + "!");
    while (table.endsWith("/")) {
      table = table.substring(0, table.length() - 1);
    }
    return table;
  }

  static byte[] getFamily(URI uri) {
    return Optional.ofNullable(UriUtil.parseQuery(uri).get(FAMILY_QUERY))
        .map(String::getBytes)
        .orElseThrow(() -> new IllegalArgumentException(
            "Query " + FAMILY_QUERY + " is missing!"));
  }

  static void closeQuietly(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception ex) {
      log.warn("Failed to close {}. Ignored.", closeable, ex);
    }
  }

  private Util() {
    // nop
  }

}
