/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.audit.dynamodb;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.duracloud.audit.AuditLogItem;
import org.duracloud.audit.AuditLogStore;
import org.duracloud.audit.AuditLogWriteFailedException;
import org.duracloud.common.collection.StreamingIterator;
import org.duracloud.common.error.DuraCloudRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.Select;
import com.amazonaws.util.json.JSONWriter;

/**
 * A DynamoDB based implementation of the <code>AuditLogStore</code>.
 * @author Daniel Bernstein 
 *         Date: 3/11/2014
 */
public class DynamoDBAuditLogStore implements AuditLogStore {

    private final Logger log =
        LoggerFactory.getLogger(DynamoDBAuditLogStore.class);

    private AmazonDynamoDBClient client;
    private DynamoDBMapper mapper;

    public DynamoDBAuditLogStore() {

    }

    public void initialize(AmazonDynamoDBClient client) {
        this.client = client;
        this.mapper = new DynamoDBMapper(client);
    }

    @Override
    public void write(String account,
                      String storeId,
                      String spaceId,
                      String contentId,
                      String contentMd5,
                      String mimetype,
                      String contentSize,
                      String user,
                      String action,
                      Map<String, String> properties,
                      Date timestamp) throws AuditLogWriteFailedException {
        checkInitialized();
        DynamoDBAuditLogItem item = null;
        
        try {
            item =
                new DynamoDBAuditLogItem(KeyUtil.calculateAuditLogHashKey(account,
                                                                          storeId,
                                                                          spaceId,
                                                                          contentId),
                                         KeyUtil.calculateAccountSpaceIdHash(account,
                                                                             spaceId),
                                         account,
                                         storeId,
                                         spaceId,
                                         contentId,
                                         contentMd5,
                                         mimetype,
                                         contentSize,
                                         user,
                                         action,
                                         properties,
                                         timestamp.getTime());
            
            
            mapper.save(item);
            log.debug("Item written:  Result: {}", item);
        } catch (AmazonClientException ex) {
            log.error("failed to write to db: {}", item);
            throw new AuditLogWriteFailedException(ex, item);
        }
    }

    @Override
    public Iterator<AuditLogItem> getLogItems(String account,
                                              String spaceId) {
        checkInitialized();
        Map<String, Condition> keyConditions = new HashMap<>();
        keyConditions.put(DynamoDBAuditLogItem.ACCOUNT_SPACE_ID_HASH_ATTRIBUTE,
                       new Condition().withComparisonOperator(ComparisonOperator.EQ)
                                      .withAttributeValueList(new AttributeValue(KeyUtil.calculateAccountSpaceIdHash(account,
                                                                                                                     spaceId))));

        QueryRequest request =
            new QueryRequest(DynamoDBAuditLogItem.TABLE_NAME).withIndexName(DynamoDBAuditLogItem.ACCOUNT_SPACE_ID_INDEX)
                                                     .withKeyConditions(keyConditions);
        request.setSelect(Select.ALL_PROJECTED_ATTRIBUTES);
        
        return new StreamingIterator<AuditLogItem>(new DynamoDBIteratorSource<AuditLogItem>(client,
                                                                           request,
                                                                           DynamoDBAuditLogItem.class));
    }
    
    @Override
    public Iterator<AuditLogItem> getLogItems(String account,
                                              String storeId,
                                              String spaceId,
                                              String contentId) {
        checkInitialized();
        Map<String, Condition> keyConditions = new HashMap<>();
        keyConditions.put(DynamoDBAuditLogItem.ID_ATTRIBUTE,
                       new Condition().withComparisonOperator(ComparisonOperator.EQ)
                                      .withAttributeValueList(new AttributeValue(KeyUtil.calculateAuditLogHashKey(account, storeId, spaceId,  contentId))));

        QueryRequest request =
            new QueryRequest(DynamoDBAuditLogItem.TABLE_NAME).withKeyConditions(keyConditions);
        request.setSelect(Select.ALL_ATTRIBUTES);
        
        return new StreamingIterator<AuditLogItem>(new DynamoDBIteratorSource<AuditLogItem>(client,
                                                                           request,
                                                                           DynamoDBAuditLogItem.class));
    }

    private void checkInitialized() {
        if (null == client) {
            StringBuilder err = new StringBuilder("AuditLogStore must be ");
            err.append("initialized!");
            log.error(err.toString());
            throw new DuraCloudRuntimeException(err.toString());
        }
    }

}
