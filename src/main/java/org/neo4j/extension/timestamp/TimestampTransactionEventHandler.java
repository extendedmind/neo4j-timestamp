package org.neo4j.extension.timestamp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.event.PropertyEntry;
import org.neo4j.graphdb.event.TransactionData;
import org.neo4j.graphdb.event.TransactionEventHandler;

/**
 * a {@see TransactionEventHandler} that
 * <ul>
 * <li>generates and updates "modified" properties for each new node and
 * relationship</li>
 * </ul>
 */
public class TimestampTransactionEventHandler<T> implements
    TransactionEventHandler<T> {

  public static final String MODIFIED_TIMESTAMP_PROPERTY_NAME = "modified";
  public static final String CREATED_TIMESTAMP_PROPERTY_NAME = "created";


  private boolean addCreated = false;
  private List<RelationshipType> skipEndNodeUpdateOnNewRelationshipsFor = null;
  private List<TimestampCustomPropertyHandler> customPropertyHandlers = null;
  
  public TimestampTransactionEventHandler(boolean addCreated,
		  List<RelationshipType> skipEndNodeUpdateOnNewRelationshipsFor,
		  List<TimestampCustomPropertyHandler> customPropertyHandlers) {
	this.addCreated = addCreated;
	this.skipEndNodeUpdateOnNewRelationshipsFor = skipEndNodeUpdateOnNewRelationshipsFor;
	this.customPropertyHandlers = customPropertyHandlers;
  }

  @Override
  public T beforeCommit(TransactionData data) throws Exception {
    long currentTime = System.currentTimeMillis();
    updateParentTimestampsFor(data.assignedRelationshipProperties(), currentTime);
    updateParentTimestampsFor(data.assignedNodeProperties(), currentTime);

    // With removed properties, don't update when node is being deleted
    updateRemoveTimestampsFor(data.removedRelationshipProperties(), data.deletedRelationships(), currentTime);
    updateRemoveTimestampsFor(data.removedNodeProperties(), data.deletedNodes(), currentTime);

    updateTimestampsFor(data.createdNodes(), currentTime);

    if (this.addCreated){
      addCreatedTimestampFor(data.createdNodes(), currentTime);
    }

    // For created relationships, update both start and end node, and relationship itself
    Iterable<Relationship> createdRelationships = data.createdRelationships();
    Set<PropertyContainer> updatedPropertyContainers = null;
    for (Relationship relationship : createdRelationships) {
      if (updatedPropertyContainers == null)
        updatedPropertyContainers = new HashSet<PropertyContainer>();
      updatedPropertyContainers.add(relationship.getStartNode());
      // Go through the limitations and only update end node if current relationship is not in the set
      boolean skipEndNodeUpdate = false;
      if (skipEndNodeUpdateOnNewRelationshipsFor != null && !skipEndNodeUpdateOnNewRelationshipsFor.isEmpty()){
        for (RelationshipType skipUpdateRelationshipType : skipEndNodeUpdateOnNewRelationshipsFor){
          if (relationship.isType(skipUpdateRelationshipType)){
        	skipEndNodeUpdate = true;
            break;
          }
        }
      }
      if (!skipEndNodeUpdate)
        updatedPropertyContainers.add(relationship.getEndNode());
    }
    updateTimestampsFor(updatedPropertyContainers, currentTime);
    updateTimestampsFor(createdRelationships, currentTime);

    if (this.addCreated){
      addCreatedTimestampFor(createdRelationships, currentTime);
    }
    
    // Process custom relationships
    if (customPropertyHandlers != null && !customPropertyHandlers.isEmpty()){
    	for (TimestampCustomPropertyHandler cpr : customPropertyHandlers){
    		for (PropertyEntry<Node> assignedProperty : data.assignedNodeProperties()){
    			if (cpr.getCustomPropertyName() == assignedProperty.key()){
    				// Assigned custom property found, process modifications
    				updateCustomRelationshipModifiedTimestampsFor(
    						assignedProperty.entity(),
    						data.deletedRelationships(),
    						cpr.getModifiedRelationshipTypes(),
    						cpr.getDirection(),
    						currentTime);
    			}
    		}
    		for (PropertyEntry<Node> removedProperty : data.removedNodeProperties()){
    			if (cpr.getCustomPropertyName() == removedProperty.key()){
    				// Removed custom property found, process modifications to relationships
    				updateCustomRelationshipModifiedTimestampsFor(
    						removedProperty.entity(),
    						data.deletedRelationships(),
    						cpr.getModifiedRelationshipTypes(),
    						cpr.getDirection(),
    						currentTime);
    			}    			
    		}
    	}
    }

    return null;
  }
  
  @Override
  public void afterCommit(TransactionData data, java.lang.Object state) {
  }

  @Override
  public void afterRollback(TransactionData data, java.lang.Object state) {
  }

  private void updateParentTimestampsFor(Iterable<? extends PropertyEntry<?>> propertyEntries, long currentTime) {
    if (propertyEntries == null) return;
    Set<PropertyContainer> updatedPropertyContainers = null;
    for (PropertyEntry<?> propertyEntry : propertyEntries) {
      if (updatedPropertyContainers == null)
        updatedPropertyContainers = new HashSet<PropertyContainer>();
      updatedPropertyContainers.add(propertyEntry.entity());
    }
    if (updatedPropertyContainers != null)
      updateTimestampsFor(updatedPropertyContainers, currentTime);
  }

  private void updateRemoveTimestampsFor(Iterable<? extends PropertyEntry<?>> propertyEntries, Iterable<? extends PropertyContainer> deletedPropertyContainers, long currentTime) {
    if (propertyEntries == null) return;
    Set<PropertyContainer> updatedPropertyContainers = null;
    for (PropertyEntry<?> propertyEntry : propertyEntries) {
      Set<?> deletedPropertyContainerSet = propertyContainersToSet(deletedPropertyContainers);
      if (deletedPropertyContainerSet == null || !deletedPropertyContainerSet.contains(propertyEntry.entity())){
        if (updatedPropertyContainers == null)
          updatedPropertyContainers = new HashSet<PropertyContainer>();
        updatedPropertyContainers.add(propertyEntry.entity());
      }
    }
    if (updatedPropertyContainers != null)
      updateTimestampsFor(updatedPropertyContainers, currentTime);
  }
  
  private void updateCustomRelationshipModifiedTimestampsFor(
		  Node node,
		  Iterable<Relationship> deletedRelationships,
		  List<RelationshipType> customRelationshipTypes,
		  Direction direction,
		  long currentTime){
	  List<Relationship> relationshipsToUpdate = null;
	  for (RelationshipType customRelationshipType : customRelationshipTypes){
		  for (Relationship relationship : node.getRelationships(direction)){
			  if (relationship.isType(customRelationshipType)){
				  // Make sure relationship hasn't been deleted, if it has, don't attempt to update anything
				  boolean relationshipDeleted = false;
				  if (deletedRelationships != null){
					  for (Relationship deletedRelationship : deletedRelationships){
						  if (deletedRelationship.getId() == relationship.getId()){
							  relationshipDeleted = true;
							  break;
						  }
					  }
				  }
				  if (!relationshipDeleted){
					  // Found a relationship to update
					  if (relationshipsToUpdate == null)
						  relationshipsToUpdate = new ArrayList<Relationship>();
					  relationshipsToUpdate.add(relationship);
				  }
			  }
		  }
	  }
	  if (relationshipsToUpdate != null){
		  List<PropertyContainer> propertyContainersToUpdate =
				  new ArrayList<PropertyContainer>(relationshipsToUpdate.size() * 2);
		  for (Relationship relationshipToUpdate : relationshipsToUpdate){			  
			  propertyContainersToUpdate.add(relationshipToUpdate);
			  // Also update the node at the other end of the relationship
			  if (relationshipToUpdate.getEndNode().getId() != node.getId()){
				  propertyContainersToUpdate.add(relationshipToUpdate.getEndNode());
			  }else if (relationshipToUpdate.getStartNode().getId() != node.getId()){
				  propertyContainersToUpdate.add(relationshipToUpdate.getStartNode());				  
			  }
		  }
		  updateTimestampsFor(propertyContainersToUpdate, currentTime);
	  }
	  
  }

  private Set<?> propertyContainersToSet(Iterable<? extends PropertyContainer> propertyContainers){
    if (propertyContainers == null) return null;
    Set<PropertyContainer> propertyContainerSet = null;
    for (PropertyContainer propertyContainer : propertyContainers){
      if (propertyContainerSet == null)
        propertyContainerSet = new HashSet<PropertyContainer>();
      propertyContainerSet.add(propertyContainer);
    }
    return propertyContainerSet;
  }

  private void updateTimestampsFor(Iterable<? extends PropertyContainer> propertyContainers, long currentTime) {
    if (propertyContainers == null) return;
    for (PropertyContainer propertyContainer : propertyContainers) {
      propertyContainer.setProperty(MODIFIED_TIMESTAMP_PROPERTY_NAME, currentTime);
    }
  }

  private void addCreatedTimestampFor(Iterable<? extends PropertyContainer> propertyContainers, long currentTime) {
    if (propertyContainers == null) return;
    for (PropertyContainer propertyContainer : propertyContainers) {
      if (!propertyContainer.hasProperty(CREATED_TIMESTAMP_PROPERTY_NAME)){
        propertyContainer.setProperty(CREATED_TIMESTAMP_PROPERTY_NAME, currentTime);
      }
    }
  }
 
}
