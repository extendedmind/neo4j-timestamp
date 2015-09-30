package org.neo4j.extension.timestamp;

import java.util.List;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.kernel.extension.KernelExtensionFactory;
import org.neo4j.kernel.lifecycle.Lifecycle;

public class TimestampKernelExtensionFactory extends KernelExtensionFactory<TimestampKernelExtensionFactory.Dependencies>
{
    private boolean setupAutoIndexing = false;
    private List<RelationshipType> skipEndNodeUpdateOnNewRelationshipsFor = null;
    private boolean addCreated = false;
    private List<TimestampCustomPropertyHandler> customPropertyHandlers = null;
    
    public interface Dependencies{
        GraphDatabaseService getDatabase();
    }
    
    public TimestampKernelExtensionFactory(boolean setupAutoIndexing, boolean addCreated,
    		List<RelationshipType> skipEndNodeUpdateOnNewRelationshipsFor, List<TimestampCustomPropertyHandler> customPropertyHandlers){
        super( "timestamp" );
        this.setupAutoIndexing = setupAutoIndexing;
        this.addCreated = addCreated;
        this.customPropertyHandlers = customPropertyHandlers;
        this.skipEndNodeUpdateOnNewRelationshipsFor = skipEndNodeUpdateOnNewRelationshipsFor;
    }
    
    public TimestampKernelExtensionFactory(boolean setupAutoIndexing, boolean addCreated){
        super( "timestamp" );
        this.setupAutoIndexing = setupAutoIndexing;
        this.addCreated = addCreated;
    }

    @Override
    public Lifecycle newKernelExtension( Dependencies dependencies ) throws Throwable {
        return new TimestampKernelExtension(dependencies.getDatabase(), this.setupAutoIndexing, this.addCreated,
        		this.skipEndNodeUpdateOnNewRelationshipsFor, this.customPropertyHandlers);
    }
}
