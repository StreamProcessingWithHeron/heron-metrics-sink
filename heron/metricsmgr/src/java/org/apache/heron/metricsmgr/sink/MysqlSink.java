/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.heron.metricsmgr.sink;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.heron.spi.metricsmgr.metrics.MetricsFilter;
import org.apache.heron.spi.metricsmgr.metrics.MetricsInfo;
import org.apache.heron.spi.metricsmgr.metrics.MetricsRecord;
import org.apache.heron.spi.metricsmgr.sink.IMetricsSink;
import org.apache.heron.spi.metricsmgr.sink.SinkContext;

public class MysqlSink implements IMetricsSink {
  private Logger LOG = Logger.getLogger(MysqlSink.class.getName());

  private SinkContext ctx; 
  private Connection conn = null; 
  private MetricsFilter mf; 

  @Override
  public void init(Map<String, Object> m, SinkContext c) {
    LOG.info("init");
    this.ctx = c;
    try {
      this.conn = DriverManager.getConnection(
        (String) m.get("mysql-site"), (String) m.get("mysql-user"),
        (String) m.get("mysql-pass")); 
      conn.setAutoCommit(false); 
    } catch (SQLException e) {
      e.printStackTrace();
    }
    this.mf = new MetricsFilter();
    ((List<String>) m.get("mysql-metric-name-prefix")).stream()
      .forEach(x -> mf.setPrefixToType(x,
        MetricsFilter.MetricAggregationType.UNKNOWN)); 
  }

  @Override
  public void processRecord(MetricsRecord record) {
    LOG.info("processRecord");
    String toponame = ctx.getTopologyName();
    String source = record.getSource();
    for (MetricsInfo mi: mf.filter(record.getMetrics())) { 
      String metricname = mi.getName();
      String value = mi.getValue(); 
      try (Statement stmt = conn.createStatement()) { 
        String sql1 = String.format(
          "INSERT IGNORE INTO serieskey " + 
          "(toponame, componentinstance, metircname) " + 
          "VALUES ('%s', '%s', '%s');",
          toponame, source, metricname); 
        stmt.executeUpdate(sql1);
        String sql2 = String.format("INSERT INTO metricval " + 
          "(seriesid, ts, val) " + 
          "SELECT seriesid, CURRENT_TIMESTAMP(), %s " + 
          "FROM serieskey WHERE toponame='%s' " + 
          "AND componentinstance='%s' AND metircname='%s';",
          value, toponame, source, metricname); 
        stmt.executeUpdate(sql2);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void flush() {
    LOG.info("flush");
    try {
      conn.commit();
    } catch (SQLException e) {
      try {
        conn.rollback();
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
    }
  }

  @Override
  public void close() {
    LOG.info("close");
    try {
      conn.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
}