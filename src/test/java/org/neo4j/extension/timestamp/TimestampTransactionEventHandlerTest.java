package org.neo4j.extension.timestamp;

import org.junit.Test;
import org.neo4j.extension.timestamp.TimestampTransactionEventHandler;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class TimestampTransactionEventHandlerTest extends TimestampTestBase {

  @Test
  public void shouldCreateAndUpdateTimestamp() {    
    GraphDatabaseService graphdb = new GraphDatabaseFactory()
      .newEmbeddedDatabaseBuilder(new java.io.File(TEST_DATA_STORE_DESTINATION)).newGraphDatabase();
    graphdb.registerTransactionEventHandler(new TimestampTransactionEventHandler<Long>(true, null, null));

    long createdNodeId = super.checkTimestampCreation(graphdb);
    super.checkTimestampUpdateOnPropertyAdd(graphdb, createdNodeId);
    super.checkTimestampUpdateOnRelationshipAdd(graphdb, createdNodeId);
  }
}
