/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL
 * license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 */

package com.mirth.connect.donkey.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.model.channel.MetaDataColumn;
import com.mirth.connect.donkey.model.channel.MetaDataColumnException;
import com.mirth.connect.donkey.model.channel.MetaDataColumnType;
import com.mirth.connect.donkey.model.channel.QueueConnectorProperties;
import com.mirth.connect.donkey.model.channel.QueueConnectorPropertiesInterface;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.ContentType;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.ChannelException;
import com.mirth.connect.donkey.server.channel.DestinationChain;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.channel.MessageResponse;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.channel.StorageSettings;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.server.data.DonkeyDao;
import com.mirth.connect.donkey.test.util.TestChannel;
import com.mirth.connect.donkey.test.util.TestDestinationConnector;
import com.mirth.connect.donkey.test.util.TestFilterTransformer;
import com.mirth.connect.donkey.test.util.TestPostProcessor;
import com.mirth.connect.donkey.test.util.TestPreProcessor;
import com.mirth.connect.donkey.test.util.TestSourceConnector;
import com.mirth.connect.donkey.test.util.TestUtils;
import com.mirth.connect.donkey.test.util.TestUtils.MessageStorageMode;

public class ChannelTests {
    final public static int TEST_SIZE = 50;

    private static String channelId = TestUtils.DEFAULT_CHANNEL_ID;
    private static String serverId = TestUtils.DEFAULT_SERVER_ID;
    private static String testMessage = TestUtils.TEST_HL7_MESSAGE;

    @BeforeClass
    final public static void beforeClass() throws StartException {
        Donkey donkey = Donkey.getInstance();
        donkey.startEngine(TestUtils.getDonkeyTestConfiguration());
    }

    @AfterClass
    final public static void afterClass() throws StartException {
        Donkey.getInstance().stopEngine();
    }

    /*
     * Deploys a channel, asserts that:
     * - The channel was successfully deployed
     * - The channel is not running
     * - The source connector was successfully deployed
     * - The source connector is not running
     * - Each destination connector was successfully deployed
     * - Each destination connector is not running
     * Then sends messages and asserts that:
     * - Each message is not received by the source connector
     */
    @Test
    public final void testDeployChannel() throws Exception {
        ChannelController.getInstance().deleteAllMessages(channelId);

        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();

        assertTrue(channel.isDeployed());
        assertFalse(channel.isRunning());
        assertTrue(sourceConnector.isDeployed());
        assertFalse(sourceConnector.isRunning());

        for (DestinationChain chain : channel.getDestinationChains()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertTrue(((TestDestinationConnector) destinationConnector).isDeployed());
                    assertFalse(destinationConnector.isRunning());
                }
            }
        }

        channel.undeploy();
    }

    /*
     * Deploys and undeploys a channel, asserts that:
     * - The channel was successfully undeployed
     * - The channel is not running
     * - The source connector was successfully undeployed
     * - The source connector is not running
     * - Each destination connector was successfully undeployed
     * - Each destination connector is not running
     * Then sends messages and asserts that:
     * - Each message is not received by the source connector
     */
    @Test
    public final void testUndeployChannel() throws Exception {
        ChannelController.getInstance().deleteAllMessages(channelId);

        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.undeploy();

        assertFalse(channel.isDeployed());
        assertFalse(channel.isRunning());
        assertFalse(sourceConnector.isDeployed());
        assertFalse(sourceConnector.isRunning());

        for (DestinationChain chain : channel.getDestinationChains()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertFalse(((TestDestinationConnector) destinationConnector).isDeployed());
                    assertFalse(destinationConnector.isRunning());
                }
            }
        }
    }

    /*
     * Deploys and starts a channel, asserts that:
     * - The channel is running
     * - The source queue is created
     * - The source connector is running
     * - Each destination connector is running
     * Then sends messages and asserts that:
     * - Each message is received by the destination connectors
     */
    @Test
    public final void testStartChannel() throws Exception {
        ChannelController.getInstance().deleteAllMessages(channelId);

        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start();

        assertTrue(channel.isRunning());
        assertNotNull(channel.getSourceQueue());
        assertTrue(sourceConnector.isRunning());

        for (DestinationChain chain : channel.getDestinationChains()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertTrue(destinationConnector.isRunning());
                }
            }
        }

        for (int i = 1; i <= TEST_SIZE; i++) {
            sourceConnector.readTestMessage(testMessage);
        }

        Thread.sleep(1000);

        for (DestinationChain chain : channel.getDestinationChains()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertEquals(((TestDestinationConnector) destinationConnector).getMessageIds().size(), TEST_SIZE);
                }
            }
        }

        channel.stop();
        channel.undeploy();
    }

    /*
     * Deploys, starts and pauses a channel, asserts that:
     * - The source connector is not running
     * Then sends messages and asserts that:
     * - Each message is not received by the destination connectors
     */
    @Test
    public final void testPauseChannel() throws Exception {
        ChannelController.getInstance().deleteAllMessages(channelId);

        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start();
        channel.pause();

        assertFalse(sourceConnector.isRunning());

        ChannelException exception = null;

        try {
            sourceConnector.readTestMessage(testMessage);
        } catch (ChannelException e) {
            exception = e;
        }

        assertNotNull(exception);
        Thread.sleep(1000);

        for (DestinationChain chain : channel.getDestinationChains()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertEquals(((TestDestinationConnector) destinationConnector).getMessageIds().size(), 0);
                }
            }
        }

        channel.stop();
        channel.undeploy();
    }

    /*
     * Deploys and starts a channel, sends messages, stops the channel and
     * asserts that:
     * - The channel is not running
     * - The source connector is not running
     * - Each destination connector is not running
     * - Each message is received by each destination connector
     * Then sends messages and asserts that:
     * - Each dispatch returns a null MessageResponse
     */
    @Test
    public final void testStopChannel() throws Exception {
        ChannelController.getInstance().deleteAllMessages(channelId);

        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start();

        for (int i = 1; i <= TEST_SIZE; i++) {
            sourceConnector.readTestMessage(testMessage);
        }

        channel.stop();
        assertFalse(channel.isRunning());
        assertFalse(sourceConnector.isRunning());

        for (DestinationChain chain : channel.getDestinationChains()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertFalse(destinationConnector.isRunning());
                    assertEquals(((TestDestinationConnector) destinationConnector).getMessageIds().size(), TEST_SIZE);
                }
            }
        }

        ChannelException exception = null;

        try {
            assertNull(sourceConnector.readTestMessage(testMessage));
        } catch (ChannelException e) {
            exception = e;
        }

        assertNotNull(exception);
        channel.undeploy();
    }

    /*
     * Deploys and starts a channel, sends messages, stops the channel and
     * asserts that:
     * - The channel is not running
     * - The source connector is not running
     * - Each destination connector is not running
     * - Each message is received by the source connector
     * Then sends messages and asserts that:
     * - Each dispatch returns a null MessageResponse
     */
    @Test
    public final void testHardStop() throws Exception {
        ChannelController.getInstance().deleteAllMessages(channelId);

        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start();

        for (int i = 1; i <= TEST_SIZE; i++) {
            sourceConnector.readTestMessage(testMessage);
        }

        channel.halt();
        assertFalse(channel.isRunning());
        assertFalse(sourceConnector.isRunning());

        for (DestinationChain chain : channel.getDestinationChains()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertFalse(destinationConnector.isRunning());
                }
            }
        }

        assertEquals(sourceConnector.getMessageIds().size(), TEST_SIZE);

        ChannelException exception = null;

        try {
            assertNull(sourceConnector.readTestMessage(testMessage));
        } catch (ChannelException e) {
            exception = e;
        }

        assertNotNull(exception);
        channel.undeploy();
    }

    /*
     * Creates a channel and asserts that:
     * - The channel exists
     * Then removes the channel and asserts that:
     * - The channel does not exist
     */
    @Test
    public final void testControllerRemoveChannel() throws Exception {
        ChannelController channelController = ChannelController.getInstance();

        // Create Channel
        TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        assertTrue(TestUtils.channelExists(channelId));

        // Delete Channel
        channelController.removeChannel(channelId);
        assertFalse(TestUtils.channelExists(channelId));
    }

    /*
     * Creates and deploys a channel, and asserts that:
     * - No extra columns exist on the custom metadata table
     * 
     * Adds metadata columns (one of each type), redeploys the channel, and
     * asserts that:
     * - All the added columns are in the database with the correct name and
     * type
     * 
     * Removes one of the columns, redeploys, and asserts that:
     * - The column is no longer in the database
     * 
     * Alters the name of one of the columns, redeploys, and asserts that:
     * - The old column is dropped correctly
     * - The new column is added correctly
     * 
     * Alters the type one of the columns, redeploys, and asserts that:
     * - The old column is dropped correctly
     * - The new column is added correctly
     * 
     * Alters the name and type of one of the columns, redeploys, and asserts
     * that:
     * - The old column is dropped correctly
     * - The new column is added correctly
     */
    @Test
    public final void testUpdateMetaDataColumns() throws Exception {
        ChannelController.getInstance().removeChannel(channelId);

        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId);

        channel.deploy();

        // Assert that there are no columns currently
        assertEquals(TestUtils.getExistingMetaDataColumns(channelId).size(), 0);

        // Add all the columns
        channel.getMetaDataColumns().add(new MetaDataColumn("stringcolumn", MetaDataColumnType.STRING, null));
        channel.getMetaDataColumns().add(new MetaDataColumn("longcolumn", MetaDataColumnType.LONG, null));
        channel.getMetaDataColumns().add(new MetaDataColumn("doublecolumn", MetaDataColumnType.DOUBLE, null));
        channel.getMetaDataColumns().add(new MetaDataColumn("booleancolumn", MetaDataColumnType.BOOLEAN, null));
        channel.getMetaDataColumns().add(new MetaDataColumn("datecolumn", MetaDataColumnType.DATE, null));
        channel.getMetaDataColumns().add(new MetaDataColumn("timecolumn", MetaDataColumnType.TIME, null));
        channel.getMetaDataColumns().add(new MetaDataColumn("timestampcolumn", MetaDataColumnType.TIMESTAMP, null));

        channel.undeploy();
        channel.deploy();

        // Assert that each column exists
        List<MetaDataColumn> columns = TestUtils.getExistingMetaDataColumns(channelId);
        assertTrue(columns.contains(new MetaDataColumn("stringcolumn", MetaDataColumnType.STRING, null)));
        assertTrue(columns.contains(new MetaDataColumn("longcolumn", MetaDataColumnType.LONG, null)));
        assertTrue(columns.contains(new MetaDataColumn("doublecolumn", MetaDataColumnType.DOUBLE, null)));
        assertTrue(columns.contains(new MetaDataColumn("booleancolumn", MetaDataColumnType.BOOLEAN, null)));
        assertTrue(columns.contains(new MetaDataColumn("datecolumn", MetaDataColumnType.DATE, null)));
        assertTrue(columns.contains(new MetaDataColumn("timecolumn", MetaDataColumnType.TIME, null)));
        assertTrue(columns.contains(new MetaDataColumn("timestampcolumn", MetaDataColumnType.TIMESTAMP, null)));

        // Remove the string column
        channel.getMetaDataColumns().remove(0);

        channel.undeploy();
        channel.deploy();

        // Assert that the string column doesn't exist anymore
        columns = TestUtils.getExistingMetaDataColumns(channelId);
        assertFalse(columns.contains(new MetaDataColumn("stringcolumn", MetaDataColumnType.STRING, null)));

        // Alter the long column's name
        channel.getMetaDataColumns().get(0).setName("longcolumn2");

        channel.undeploy();
        channel.deploy();

        // Assert that the long column got dropped/added correctly
        columns = TestUtils.getExistingMetaDataColumns(channelId);
        assertFalse(columns.contains(new MetaDataColumn("longcolumn", MetaDataColumnType.LONG, null)));
        assertTrue(columns.contains(new MetaDataColumn("longcolumn2", MetaDataColumnType.LONG, null)));

        // Alter the double column's type
        channel.getMetaDataColumns().get(1).setType(MetaDataColumnType.TIMESTAMP);

        channel.undeploy();
        channel.deploy();

        // Assert that the double column got dropped/added correctly as a timestamp column
        columns = TestUtils.getExistingMetaDataColumns(channelId);
        assertFalse(columns.contains(new MetaDataColumn("doublecolumn", MetaDataColumnType.DOUBLE, null)));
        assertTrue(columns.contains(new MetaDataColumn("doublecolumn", MetaDataColumnType.TIMESTAMP, null)));

        // Alter the boolean column's name and type
        channel.getMetaDataColumns().get(2).setName("booleancolumn2");
        channel.getMetaDataColumns().get(2).setType(MetaDataColumnType.TIME);

        channel.undeploy();
        channel.deploy();

        // Assert that the boolean column got dropped/added correctly as a time column
        columns = TestUtils.getExistingMetaDataColumns(channelId);
        assertFalse(columns.contains(new MetaDataColumn("booleancolumn", MetaDataColumnType.BOOLEAN, null)));
        assertTrue(columns.contains(new MetaDataColumn("booleancolumn2", MetaDataColumnType.TIME, null)));

        channel.undeploy();
    }

    @Test
    public final void testMetaDataCasting() throws MetaDataColumnException {
        MetaDataColumnType columnType = MetaDataColumnType.BOOLEAN;
        Boolean booleanValue = (Boolean) columnType.castMetaDataFromString("TRUE");
        assertEquals(Boolean.TRUE, booleanValue);
        booleanValue = (Boolean) columnType.castMetaDataFromString("FALSE");
        assertEquals(Boolean.FALSE, booleanValue);

        columnType = MetaDataColumnType.DOUBLE;
        Double doubleValue = (Double) columnType.castMetaDataFromString("1.0234567890123456789");
        assertEquals(new Double(1.0234567890123456789), doubleValue);

        columnType = MetaDataColumnType.LONG;
        Long longValue = (Long) columnType.castMetaDataFromString("1234567890123456789");
        assertEquals(new Long(1234567890123456789l), longValue);

        columnType = MetaDataColumnType.STRING;
        String stringValue = (String) columnType.castMetaDataFromString(" test !@# String 123 ");
        assertEquals(" test !@# String 123 ", stringValue);

        columnType = MetaDataColumnType.DATE;
        Calendar dateValue = (Calendar) columnType.castMetaDataFromString("2010-01-02");
        assertEquals("01 02 2010", new SimpleDateFormat("MM dd yyyy").format(dateValue.getTimeInMillis()));

        columnType = MetaDataColumnType.TIME;
        dateValue = (Calendar) columnType.castMetaDataFromString("13:01:02");
        assertEquals("13 01 02", new SimpleDateFormat("HH mm ss").format(dateValue.getTimeInMillis()));

        columnType = MetaDataColumnType.TIMESTAMP;
        dateValue = (Calendar) columnType.castMetaDataFromString("2010-01-02 13:01:02");
        assertEquals("13 01 02 01 02 2010", new SimpleDateFormat("HH mm ss MM dd yyyy").format(dateValue.getTimeInMillis()));
    }

    /*
     * Create a new test channel
     * Process a source message with a metadata ID of 1, assert that:
     * - An InvalidConnectorMessageState exception is thrown
     * 
     * Process a source message with a status other than RECEIVED, assert that:
     * - An InvalidConnectorMessageState exception is thrown
     * 
     * Process a valid source message, and assert that:
     * - The pre-processor was run
     * - The processed raw content was stored
     * - The filter/transformer was run
     * - The transformed/encoded content was stored
     * - Initial messages were created for each destination chain
     * - The message processed through at least the first destination connector
     * for each chain
     * - The post-processor was run
     * - The final transaction was created
     */
    @Test
    public final void testProcess() throws Exception {
        ChannelController.getInstance().deleteAllMessages(channelId);
        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId);
        DonkeyDao dao = null;

        channel.deploy();
        channel.start();

        dao = Donkey.getInstance().getDaoFactory().getDao();
        ConnectorMessage sourceMessage = null;

        try {
            sourceMessage = TestUtils.createAndStoreNewMessage(new RawMessage(testMessage), channelId, serverId, dao).getConnectorMessages().get(0);
            dao.commit();
        } finally {
            dao.close();
        }

        Message message = null;

        message = channel.process(sourceMessage, false);

        // Assert that the message was run through the pre-processor
        assertTrue(((TestPreProcessor) channel.getPreProcessor()).isProcessed());

        // Assert that the processed raw content was stored
        TestUtils.assertMessageContentExists(sourceMessage.getProcessedRaw());

        // Assert that the FilterTransformer was run
        assertTrue(((TestFilterTransformer) channel.getSourceFilterTransformer().getFilterTransformer()).isTransformed());

        // Assert that the transformed/encoded content was stored
        TestUtils.assertMessageContentExists(sourceMessage.getTransformed());
        TestUtils.assertMessageContentExists(sourceMessage.getEncoded());

        for (DestinationChain chain : channel.getDestinationChains()) {
            Integer firstId = null;
            for (Integer metaDataId : chain.getDestinationConnectors().keySet()) {
                if (firstId == null || metaDataId < firstId) {
                    firstId = metaDataId;
                }
            }
            // Assert that messages were created for each destination chain
            TestUtils.assertConnectorMessageExists(message.getConnectorMessages().get(firstId), false);
            // Assert that the message processed through at least the first destination connector for each chain
            assertTrue(((TestDestinationConnector) chain.getDestinationConnectors().get(firstId)).getMessageIds().size() > 0);
        }

        // Assert that the message was run through the post-processor
        assertTrue(((TestPostProcessor) channel.getPostProcessor()).isProcessed());

        channel.stop();
        channel.undeploy();
    }

    @Test
    public final void testContentRemoval() throws Exception {
        testContentRemoval(false, false);
        testContentRemoval(true, false);
    }

    @Test
    public final void testContentRemovalWithQueueing() throws Exception {
        testContentRemoval(false, true);
        testContentRemoval(true, true);
    }

    private void testContentRemoval(boolean removeContentOnCompletion, boolean useQueue) throws Exception {
        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId);
        channel.setRemoveContentOnCompletion(removeContentOnCompletion);

        if (useQueue) {
            QueueConnectorProperties queueConnectorProperties = ((QueueConnectorPropertiesInterface) channel.getDestinationConnector(1).getConnectorProperties()).getQueueConnectorProperties();
            queueConnectorProperties.setQueueEnabled(true);
            queueConnectorProperties.setSendFirst(false);
        }

        SourceConnector sourceConnector = channel.getSourceConnector();

        channel.deploy();
        channel.start();

        MessageResponse messageResponse = sourceConnector.handleRawMessage(new RawMessage(testMessage));
        sourceConnector.storeMessageResponse(messageResponse);

        // if queueing, give the queue time to flush out
        if (useQueue) {
            Thread.sleep(1000);
        }

        channel.stop();
        channel.undeploy();

        for (ConnectorMessage connectorMessage : messageResponse.getProcessedMessage().getConnectorMessages().values()) {
            boolean foundContent = false;

            for (ContentType contentType : ContentType.values()) {
                MessageContent messageContent = connectorMessage.getContent(contentType);

                if (messageContent != null && (messageContent.getMetaDataId() == 0 || messageContent.getContentType() != ContentType.RAW)) {
                    foundContent = true;

                    if (removeContentOnCompletion) {
                        TestUtils.assertMessageContentDoesNotExist(messageContent);
                    } else {
                        TestUtils.assertMessageContentExists(messageContent);
                    }
                }
            }

            assertTrue(foundContent);
        }
    }

    @Test
    public final void testContentStorageDevelopment() throws Exception {
        testContentStorageSettings(TestUtils.getStorageSettings(MessageStorageMode.DEVELOPMENT, false), TestUtils.getStorageSettings(MessageStorageMode.DEVELOPMENT, true));
    }

    @Test
    public final void testContentStorageProduction() throws Exception {
        testContentStorageSettings(TestUtils.getStorageSettings(MessageStorageMode.PRODUCTION, false), TestUtils.getStorageSettings(MessageStorageMode.PRODUCTION, true));
    }

    @Test
    public final void testContentStorageMetadata() throws Exception {
        testContentStorageSettings(TestUtils.getStorageSettings(MessageStorageMode.METADATA, false), TestUtils.getStorageSettings(MessageStorageMode.METADATA, true));
    }

    @Test
    public final void testContentStorageDisabled() throws Exception {
        testContentStorageSettings(TestUtils.getStorageSettings(MessageStorageMode.DISABLED, false), TestUtils.getStorageSettings(MessageStorageMode.DISABLED, true));
    }

    private void testContentStorageSettings(StorageSettings sourceStorageSettings, StorageSettings destinationStorageSettings) throws Exception {
        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId);
        SourceConnector sourceConnector = channel.getSourceConnector();
        sourceConnector.setStorageSettings(sourceStorageSettings);
        channel.getDestinationConnector(1).setStorageSettings(destinationStorageSettings);

        channel.deploy();
        channel.start();

        MessageResponse messageResponse = sourceConnector.handleRawMessage(new RawMessage(testMessage));
        sourceConnector.storeMessageResponse(messageResponse);

        channel.stop();
        channel.undeploy();

        ConnectorMessage sourceMessage = messageResponse.getProcessedMessage().getConnectorMessages().get(0);
        ConnectorMessage destinationMessage = messageResponse.getProcessedMessage().getConnectorMessages().get(1);

        assertNotNull(sourceMessage);
        assertNotNull(destinationMessage);

        if (sourceStorageSettings.isStoreRaw()) {
            TestUtils.assertMessageContentExists(sourceMessage.getRaw());
            TestUtils.assertMessageContentExists(sourceMessage.getEncoded());
        } else {
            if (!sourceStorageSettings.isStoreEncoded()) {
                TestUtils.assertMessageContentDoesNotExist(sourceMessage.getRaw());
            }

            TestUtils.assertMessageContentDoesNotExist(sourceMessage.getEncoded());
        }

        if (sourceStorageSettings.isStoreProcessedRaw()) {
            TestUtils.assertMessageContentExists(sourceMessage.getProcessedRaw());
        } else {
            TestUtils.assertMessageContentDoesNotExist(sourceMessage.getProcessedRaw());
        }

        if (sourceStorageSettings.isStoreTransformed()) {
            TestUtils.assertMessageContentExists(sourceMessage.getTransformed());
        } else {
            TestUtils.assertMessageContentDoesNotExist(sourceMessage.getTransformed());
        }

        if (destinationStorageSettings.isStoreTransformed()) {
            TestUtils.assertMessageContentExists(destinationMessage.getTransformed());
        } else {
            TestUtils.assertMessageContentDoesNotExist(destinationMessage.getTransformed());
        }

        if (sourceStorageSettings.isStoreEncoded()) {
            TestUtils.assertMessageContentExists(sourceMessage.getEncoded());
        } else if (!sourceStorageSettings.isStoreRaw()) {
            TestUtils.assertMessageContentDoesNotExist(sourceMessage.getEncoded());
        }

        if (destinationStorageSettings.isStoreEncoded()) {
            TestUtils.assertMessageContentExists(destinationMessage.getEncoded());
        } else {
            TestUtils.assertMessageContentDoesNotExist(destinationMessage.getEncoded());
        }

        if (destinationStorageSettings.isStoreSent()) {
            TestUtils.assertMessageContentExists(destinationMessage.getSent());
        } else {
            TestUtils.assertMessageContentDoesNotExist(new MessageContent(channelId, messageResponse.getMessageId(), 1, ContentType.SENT, null, false));
        }

        if (destinationStorageSettings.isStoreResponse()) {
            TestUtils.assertMessageContentExists(destinationMessage.getResponse());
        } else {
            TestUtils.assertMessageContentDoesNotExist(destinationMessage.getResponse());
        }

        if (destinationStorageSettings.isStoreProcessedResponse()) {
            TestUtils.assertMessageContentExists(destinationMessage.getProcessedResponse());
        } else {
            TestUtils.assertMessageContentDoesNotExist(destinationMessage.getProcessedResponse());
        }
    }
}