/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License; you may not use this file
 * except in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.connect.jdbc.sink;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import io.confluent.connect.jdbc.util.ConfigurableJdbcCredentialsProvider;
import io.confluent.connect.jdbc.util.DefaultJdbcCredentialsProvider;
import io.confluent.connect.jdbc.util.JdbcCredentialsProvider;
import io.confluent.connect.jdbc.util.TableType;

import org.apache.kafka.common.config.ConfigException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JdbcSinkConfigTest {

  private Map<String, String> props = new HashMap<>();
  private JdbcSinkConfig config;

  @Before
  public void beforeEach() {
    // add the minimum settings only
    props.put("connection.url", "jdbc:mysql://something"); // we won't connect
  }

  @After
  public void afterEach() {
    props.clear();
    config = null;
  }

  @Test(expected = ConfigException.class)
  public void shouldFailToCreateConfigWithoutConnectionUrl() {
    props.remove(JdbcSinkConfig.CONNECTION_URL);
    createConfig();
  }

  @Test(expected = ConfigException.class)
  public void shouldFailToCreateConfigWithEmptyTableNameFormat() {
    props.put(JdbcSinkConfig.TABLE_NAME_FORMAT, "");
    createConfig();
  }

  @Test
  public void shouldCreateConfigWithMinimalConfigs() {
    createConfig();
    assertTableTypes(TableType.TABLE);
  }

  @Test
  public void shouldCreateConfigWithHoldlock() {
    createConfig();
    assertEquals(true, config.useHoldlockInMerge);
  }

  @Test
  public void shouldCreateConfigWithNoHoldlock() {
    props.put("mssql.use.merge.holdlock", "false");
    createConfig();
    assertEquals(false, config.useHoldlockInMerge);
  }

  @Test
  public void shouldCreateConfigWithAdditionalConfigs() {
    props.put("auto.create", "true");
    props.put("pk.mode", "kafka");
    props.put("pk.fields", "kafka_topic,kafka_partition,kafka_offset");
    createConfig();
    assertTableTypes(TableType.TABLE);
  }

  @Test
  public void shouldCreateConfigWithViewOnly() {
    props.put("table.types", "view");
    createConfig();
    assertTableTypes(TableType.VIEW);
  }

  @Test
  public void shouldCreateConfigWithTableOnly() {
    props.put("table.types", "table");
    createConfig();
    assertTableTypes(TableType.TABLE);
  }

  @Test
  public void shouldCreateConfigWithPartitionedTableOnly() {
    props.put("table.types", "partitioned table");
    createConfig();
    assertTableTypes(TableType.PARTITIONED_TABLE);
  }

  @Test
  public void shouldCreateConfigWithViewAndTable() {
    props.put("table.types", "view,table");
    createConfig();
    assertTableTypes(TableType.TABLE, TableType.VIEW);
    props.put("table.types", "table,view");
    createConfig();
    assertTableTypes(TableType.TABLE, TableType.VIEW);
    props.put("table.types", "table , view");
    createConfig();
    assertTableTypes(TableType.TABLE, TableType.VIEW);
  }

  @Test
  public void shouldCreateConfigWithLeadingWhitespaceInTableTypes() {
    props.put("table.types", " \t\n  view");
    createConfig();
    assertTableTypes(TableType.VIEW);
  }

  @Test
  public void shouldCreateConfigWithTrailingWhitespaceInTableTypes() {
    props.put("table.types", "table \t \n");
    createConfig();
    assertTableTypes(TableType.TABLE);
  }

  @Test
  public void shouldCreateConfigWithValidCredentialsProviderClass() {
    props.put(JdbcSinkConfig.CREDENTIALS_PROVIDER_CLASS_CONFIG,
        DefaultJdbcCredentialsProvider.class.getName());
    createConfig();
    JdbcCredentialsProvider provider = config.credentialsProvider();
    assertNotNull(provider);
    assertTrue(provider instanceof DefaultJdbcCredentialsProvider);
  }

  @Test(expected = ConfigException.class)
  public void shouldFailToCreateConfigWithInvalidCredentialsProviderClass() {
    // Configuring SqliteHelper Class here which does not extends JdbcCredentialsProvider Interface
    props.put(JdbcSinkConfig.CREDENTIALS_PROVIDER_CLASS_CONFIG,
        SqliteHelper.class.getName());
    createConfig();
  }

  @Test
  public void testConfigurableCredentialsProviderClass() {
    // Test username and password value
    String username = "test_user";
    String password = "test_password";

    props.put(JdbcSinkConfig.CREDENTIALS_PROVIDER_CLASS_CONFIG,
        ConfigurableJdbcCredentialsProvider.class.getName());

    // Adding custom config with prefix - jdbc.credentials.provider. to verify Configurable
    // functionality
    props.put(JdbcSinkConfig.CREDENTIALS_PROVIDER_CONFIG_PREFIX + "username", username);
    props.put(JdbcSinkConfig.CREDENTIALS_PROVIDER_CONFIG_PREFIX + "password", password);

    createConfig();
    JdbcCredentialsProvider provider = config.credentialsProvider();
    assertNotNull(provider);
    assertTrue(provider instanceof ConfigurableJdbcCredentialsProvider);

    // Assert Username and password are returned from config provider instance correctly
    assertEquals(username, provider.getJdbcCredentials().getUsername());
    assertEquals(password, provider.getJdbcCredentials().getPassword());
  }

  @Test
  public void testDefaultBehaviorWhenConnectionConfigsArePresent() {
    // Test username and password value
    String username = "test_user";
    String password = "test_password";

    props.put(JdbcSinkConfig.CONNECTION_USER , username);
    props.put(JdbcSinkConfig.CONNECTION_PASSWORD, password);

    createConfig();
    JdbcCredentialsProvider provider = config.credentialsProvider();
    assertNotNull(provider);
    assertTrue(provider instanceof DefaultJdbcCredentialsProvider);

    // Assert username and password are updated in provider class instance according to
    // connection.user and connection.password config values.
    assertEquals(username, provider.getJdbcCredentials().getUsername());
    assertEquals(password, provider.getJdbcCredentials().getPassword());
  }

  protected void createConfig() {
    config = new JdbcSinkConfig(props);
  }

  protected void assertTableTypes(TableType...types) {
    EnumSet<TableType> expected = EnumSet.copyOf(Arrays.asList(types));
    EnumSet<TableType> tableTypes = config.tableTypes;
    assertEquals(expected, tableTypes);
  }

}