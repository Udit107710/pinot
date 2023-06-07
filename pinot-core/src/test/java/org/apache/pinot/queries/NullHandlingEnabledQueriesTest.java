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
package org.apache.pinot.queries;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.pinot.common.response.broker.BrokerResponseNative;
import org.apache.pinot.common.response.broker.ResultTable;
import org.apache.pinot.segment.local.indexsegment.immutable.ImmutableSegmentLoader;
import org.apache.pinot.segment.local.segment.creator.impl.SegmentIndexCreationDriverImpl;
import org.apache.pinot.segment.local.segment.readers.GenericRowRecordReader;
import org.apache.pinot.segment.spi.ImmutableSegment;
import org.apache.pinot.segment.spi.IndexSegment;
import org.apache.pinot.segment.spi.creator.SegmentGeneratorConfig;
import org.apache.pinot.spi.config.table.TableConfig;
import org.apache.pinot.spi.config.table.TableType;
import org.apache.pinot.spi.data.FieldSpec;
import org.apache.pinot.spi.data.Schema;
import org.apache.pinot.spi.data.readers.GenericRow;
import org.apache.pinot.spi.utils.ReadMode;
import org.apache.pinot.spi.utils.builder.TableConfigBuilder;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;


public class NullHandlingEnabledQueriesTest extends BaseQueriesTest {
  private static final File INDEX_DIR = new File(FileUtils.getTempDirectory(), "NullHandlingEnabledQueriesTest");
  private static final String RAW_TABLE_NAME = "testTable";
  private static final String SEGMENT_NAME = "testSegment";
  private static final String COLUMN1 = "column1";

  private IndexSegment _indexSegment;
  private List<IndexSegment> _indexSegments;

  @Override
  protected String getFilter() {
    return "";
  }

  @Override
  protected IndexSegment getIndexSegment() {
    return _indexSegment;
  }

  @Override
  protected List<IndexSegment> getIndexSegments() {
    return _indexSegments;
  }

  private void setUp(TableConfig tableConfig, List<GenericRow> records)
      throws Exception {
    FileUtils.deleteDirectory(INDEX_DIR);
    Schema schema = new Schema.SchemaBuilder().addSingleValueDimension(COLUMN1, FieldSpec.DataType.INT).build();
    SegmentGeneratorConfig segmentGeneratorConfig = new SegmentGeneratorConfig(tableConfig, schema);
    segmentGeneratorConfig.setTableName(RAW_TABLE_NAME);
    segmentGeneratorConfig.setSegmentName(SEGMENT_NAME);
    segmentGeneratorConfig.setNullHandlingEnabled(true);
    segmentGeneratorConfig.setOutDir(INDEX_DIR.getPath());

    SegmentIndexCreationDriverImpl driver = new SegmentIndexCreationDriverImpl();
    driver.init(segmentGeneratorConfig, new GenericRowRecordReader(records));
    driver.build();

    ImmutableSegment immutableSegment = ImmutableSegmentLoader.load(new File(INDEX_DIR, SEGMENT_NAME), ReadMode.mmap);
    _indexSegment = immutableSegment;
    _indexSegments = Arrays.asList(immutableSegment, immutableSegment);
  }

  @Test
  public void testSelectDistinctOrderByNullsFirst()
      throws Exception {
    FileUtils.deleteDirectory(INDEX_DIR);
    GenericRow nonNullRow = new GenericRow();
    nonNullRow.putValue(COLUMN1, 1);
    GenericRow nullRow = new GenericRow();
    nullRow.putValue(COLUMN1, null);
    List<GenericRow> rows = Arrays.asList(nonNullRow, nullRow);
    TableConfig tableConfig = new TableConfigBuilder(TableType.OFFLINE).setTableName(RAW_TABLE_NAME).build();
    setUp(tableConfig, rows);
    Map<String, String> queryOptions = new HashMap<>();
    queryOptions.put("enableNullHandling", "true");
    String query = String.format("SELECT DISTINCT %s FROM testTable ORDER BY %s NULLS FIRST", COLUMN1, COLUMN1);

    BrokerResponseNative brokerResponse = getBrokerResponse(query, queryOptions);

    ResultTable resultTable = brokerResponse.getResultTable();
    assertNull(resultTable.getRows().get(0)[0]);
    assertNotNull(resultTable.getRows().get(1)[0]);
  }

  @Test
  public void testSelectDistinctOrderByNullsLast()
      throws Exception {
    FileUtils.deleteDirectory(INDEX_DIR);
    GenericRow nonNullRow = new GenericRow();
    nonNullRow.putValue(COLUMN1, 1);
    GenericRow nullRow = new GenericRow();
    nullRow.putValue(COLUMN1, null);
    List<GenericRow> rows = Arrays.asList(nonNullRow, nullRow);
    TableConfig tableConfig = new TableConfigBuilder(TableType.OFFLINE).setTableName(RAW_TABLE_NAME).build();
    setUp(tableConfig, rows);
    Map<String, String> queryOptions = new HashMap<>();
    queryOptions.put("enableNullHandling", "true");
    String query = String.format("SELECT DISTINCT %s FROM testTable ORDER BY %s NULLS LAST", COLUMN1, COLUMN1);

    BrokerResponseNative brokerResponse = getBrokerResponse(query, queryOptions);

    ResultTable resultTable = brokerResponse.getResultTable();
    assertNotNull(resultTable.getRows().get(0)[0]);
    assertNull(resultTable.getRows().get(1)[0]);
  }
}