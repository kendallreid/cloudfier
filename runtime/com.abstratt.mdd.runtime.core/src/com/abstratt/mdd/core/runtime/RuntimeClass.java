package com.abstratt.mdd.core.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.eclipse.core.runtime.Assert;
import org.eclipse.uml2.uml.BehavioredClassifier;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Parameter;
import org.eclipse.uml2.uml.ParameterSet;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.Vertex;

import com.abstratt.blobstore.IBlobStore;
import com.abstratt.blobstore.IBlobStoreCatalog;
import com.abstratt.mdd.core.runtime.types.BasicType;
import com.abstratt.mdd.core.runtime.types.CollectionType;
import com.abstratt.mdd.core.util.StateMachineUtils;
import com.abstratt.nodestore.INodeKey;
import com.abstratt.nodestore.INodeStore;
import com.abstratt.nodestore.INodeStoreCatalog;
import com.abstratt.nodestore.IntegerKey;
import com.abstratt.nodestore.NodeReference;

/**
 */
public class RuntimeClass implements MetaClass<RuntimeObject> {

    static RuntimeClass newClass(Classifier classifier, Runtime runtime) {
        return new RuntimeClass(classifier, runtime);
    }

    private Classifier classifier;

    private RuntimeClassObject classObject;

    protected Runtime runtime;

    /**
     * @param className
     * @param classifier
     * @param runtime
     */
    protected RuntimeClass(Classifier classifier, Runtime runtime) {
        Assert.isNotNull(runtime);
        Assert.isNotNull(classifier);
        this.classifier = classifier;
        this.runtime = runtime;
        this.classObject = new RuntimeClassObject(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RuntimeClass other = (RuntimeClass) obj;
        if (classifier == null) {
            if (other.classifier != null)
                return false;
        } else if (!classifier.equals(other.classifier))
            return false;
        if (runtime == null) {
            if (other.runtime != null)
                return false;
        } else if (!runtime.equals(other.runtime))
            return false;
        return true;
    }

    public Map<Operation, List<Vertex>> findStateSpecificOperations() {
        return StateMachineUtils.findStateSpecificOperations((BehavioredClassifier) getModelClassifier());
    }

    @Override
    public List<RuntimeObject> getAllInstances(Classifier classifier, boolean includeSubTypes) {
        return runtime.getAllInstances(classifier, includeSubTypes);
    }

    public final Collection<RuntimeObject> getAllInstances() {
        if (!this.isPersistable())
            return Collections.emptySet();
        Collection<RuntimeObject> fromDB = nodesToRuntimeObjects(getNodeStore().getNodeKeys());
        fromDB.addAll(getRuntime().getCurrentContext().getWorkingObjects(this));
        return fromDB;
    }

    public final CollectionType getExtent() {
        return CollectionType.createCollection(getModelClassifier(), getAllInstances());
    }

    private boolean isPersistable() {
        return !classifier.isAbstract() && classifier.eClass() == UMLPackage.Literals.CLASS;
    }

    Collection<RuntimeObject> findInstances(Map<Property, List<BasicType>> criteria, Integer limit) {
        Map<String, Collection<Object>> nodeCriteria = new LinkedHashMap<String, Collection<Object>>();
        for (Entry<Property, List<BasicType>> entry : criteria.entrySet()) {
            Collection<Object> values = new LinkedHashSet<Object>();
            for (BasicType basicType : entry.getValue()) {
                Object basicValue = (basicType instanceof RuntimeObject) ? ((RuntimeObject) basicType).nodeReference()
                        : RuntimeObject.toExternalValue(basicType);
                values.add(basicValue);
            }
            nodeCriteria.put(entry.getKey().getName(), values);
        }
        INodeStore nodeStore = getNodeStore();
        Collection<INodeKey> filtered = nodeStore.filter(nodeCriteria, limit);
        Collection<RuntimeObject> runtimeObjects = nodesToRuntimeObjects(filtered);
        return runtimeObjects;
    }

    public RuntimeObject findOneInstance(Map<Property, List<BasicType>> criteria) {
        Collection<RuntimeObject> runtimeObjects = findInstances(criteria, 1);
        return runtimeObjects.isEmpty() ? null : runtimeObjects.iterator().next();
    }

    public final RuntimeClassObject getClassObject() {
        return classObject;
    }

    public RuntimeObject getInstance(INodeKey key) {
        return getOrLoadInstance(key);
    }

    public RuntimeObject getInstance(String objectId) {
        return getOrLoadInstance(objectIdToKey(objectId));
    }

    public final Classifier getModelClassifier() {
        return classifier;
    }

    public INodeStore getNodeStore() {
        String storeName = getNodeStoreName();
        INodeStore nodeStore = getNodeStoreCatalog().getStore(storeName);
        if (nodeStore == null)
            throw new IllegalStateException("No node store for " + storeName);
        return nodeStore;
    }

    String getNodeStoreName() {
        String classifierName = getModelClassifier().getQualifiedName();
		return fromClassifierNameToStoreName(classifierName);
    }

	public static String fromClassifierNameToStoreName(String classifierName) {
		return classifierName.replaceAll(NamedElement.SEPARATOR, ".");
	}
	
	public static String fromStoreNameToClassifierName(String storeName) {
		return storeName.replaceAll("\\.", NamedElement.SEPARATOR);
	}

    public Collection<RuntimeObject> getParameterDomain(String externalId, Parameter parameter,
            Classifier parameterType) {
    	
        IntegerKey key = externalId != null ? objectIdToKey(externalId) : new IntegerKey(-1);
        RuntimeObject instance = getOrLoadInstance(key, this::createRuntimeObject);
        if (instance == null) {
        	instance = createRuntimeObject(key);
        }
		return instance.getParameterDomain(parameter, parameterType);
    }

    public Collection<RuntimeObject> getPropertyDomain(String objectId, Property property, Classifier propertyType) {
        IntegerKey key = objectIdToKey(objectId);
        if (!getNodeStore().containsNode(key))
            return Collections.emptySet();
        RuntimeObject instance = getOrLoadInstance(key);
        Collection<RuntimeObject> propertyDomain = instance.getPropertyDomain(property, propertyType);
        Collection<RuntimeObject> alreadyRelated = getRelatedInstances(objectId, property);
        propertyDomain.removeAll(alreadyRelated);
        return propertyDomain;
    }

    /**
     * Returns the related instances via the given property, even if the
     * association is polymorphic.
     */
    public Collection<RuntimeObject> getRelatedInstances(String objectId, Property property) {
        Classifier baseClass = (Classifier) property.getType();
        List<RuntimeObject> collected = getRuntime().collectInstancesFromHierarchy(baseClass, true,
                currentClass -> getRelatedInstancesOfTheExactType(objectId, property, currentClass));
        return CollectionType.createCollectionBackEndFor(property, collected);
    }
    
    private RuntimeObject createRuntimeObject(INodeKey key) {
        return new RuntimeObject(this, key);
    }

    /**
     * This helper method will traverse the relationship looking for related
     * instances of the exact type requested, which is required given that in a
     * polymorphic relationship, we will find related instances on different
     * node stores (one node store per concrete object type).
     */
    private Collection<RuntimeObject> getRelatedInstancesOfTheExactType(String objectId, Property property,
            Classifier propertyType) {
        IntegerKey key = objectIdToKey(objectId);
        if (!getNodeStore().containsNode(key))
            return Collections.emptyList();
        RuntimeObject loaded = getOrLoadInstance(key);
        if (loaded == null)
            return Collections.emptyList();
        return CollectionType.createCollectionBackEndFor(property, loaded.getRelated(property, propertyType));
    }

    public Runtime getRuntime() {
        return runtime;
    }

    @Override
    public void handleEvent(RuntimeEvent runtimeEvent) {
        // ensure target is active or it can't handle events
        RuntimeObject target = (RuntimeObject) runtimeEvent.getTarget();
        if (target.isActive())
            target.handleEvent(runtimeEvent);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (classifier == null ? 0 : classifier.hashCode());
        result = prime * result + (runtime == null ? 0 : runtime.hashCode());
        return result;
    }

    public final RuntimeObject newInstance() {
        return newInstance(true);
    }

    public final RuntimeObject newInstance(boolean persistent) {
        return newInstance(persistent, true);
    }

    /**
     * Creates a new instance of the class represented. Adds the created
     * instance to the pool of instances of the class represented.
     *
     * @param persistent
     *            whether the object is intended to be persisted (this is
     *            overruled if the context is read only, as no objects can be
     *            persisted in that case)
     * @param initDefaults
     *            whether to initialize defaults
     * @return the created instance
     */
    public final RuntimeObject newInstance(boolean persistent, boolean initDefaults) {
        if (classifier.isAbstract())
            throw new CannotInstantiateAbstractClassifier(classifier);
        RuntimeObject newObject;

        if (persistent && !runtime.getCurrentContext().isReadOnly()) {
            newObject = new RuntimeObject(this, getNodeStoreCatalog().newNode(getNodeStoreName()));
        } else
            newObject = new RuntimeObject(this);
        if (initDefaults)
            newObject.initDefaults();
        return newObject;
    }
    
    @Override
    public BasicType runOperation(ExecutionContext context, BasicType target, Operation operation, ParameterSet parameterSet,
            BasicType... arguments) {
        if (operation.isStatic())
            return getClassObject().runBehavioralFeature(operation, parameterSet, arguments);
        return ((RuntimeObject) target).runBehavioralFeature(operation, parameterSet, arguments);
    }

    @Override
    public final BasicType runOperation(ExecutionContext context, BasicType target, Operation operation,
            BasicType... arguments) {
        return runOperation(context, target, operation, null, arguments);
    }

    protected RuntimeObject getDetachedInstance(INodeKey key) {
        return new RuntimeObject(this, key);
    }

    protected RuntimeObject getOrLoadInstance(INodeKey key) {
        return getOrLoadInstance(key, this::createRuntimeObject);
    }
    
    protected <RO extends RuntimeObject> RO getOrLoadInstance(INodeKey key, Function<INodeKey, RO> objectSupplier) {
        RO existing = (RO) getRuntime().getCurrentContext()
                .getWorkingObject(new NodeReference(getNodeStoreName(), key));
        if (existing != null) {
            if (!existing.isActive())
                return null;
            return existing;
        }
        RO runtimeObject = objectSupplier.apply(key);
        try {
            // force load (also ensures the object exists)
            runtimeObject.load();
            return runtimeObject;
        } catch (NotFoundException e) {
            return null;
        }
    }

    protected Collection<RuntimeObject> nodesToRuntimeObjects(Collection<INodeKey> keys) {
        Collection<RuntimeObject> result = new LinkedHashSet<RuntimeObject>();
        for (INodeKey key : keys) {
            RuntimeObject loaded = getInstance(key);
            if (loaded != null)
                result.add(loaded);
        }
        return result;
    }

    protected IntegerKey objectIdToKey(String objectId) {
        return new IntegerKey(Long.parseLong(objectId));
    }

    INodeStoreCatalog getNodeStoreCatalog() {
        return runtime.getNodeStoreCatalog();
    }

    public IBlobStore getBlobStore() {
        IBlobStoreCatalog getBlogStoreCatalog = runtime.getBlobStoreCatalog();
        return getBlogStoreCatalog.getBlobStore();
    }
}