package com.abstratt.kirra.mdd.rest.impl.v1.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.restlet.data.Form;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.ServerResource;

import com.abstratt.kirra.Entity;
import com.abstratt.kirra.Instance;
import com.abstratt.kirra.KirraException;
import com.abstratt.kirra.Operation;
import com.abstratt.kirra.Parameter;
import com.abstratt.kirra.Repository;
import com.abstratt.kirra.Service;
import com.abstratt.kirra.TypeRef.TypeKind;
import com.abstratt.kirra.mdd.rest.KirraRESTUtils;
import com.abstratt.kirra.mdd.rest.KirraReferenceBuilder;
import com.abstratt.kirra.mdd.rest.KirraReferenceUtils;
import com.abstratt.kirra.mdd.rest.impl.v1.representation.InstanceJSONRepresentation;
import com.abstratt.kirra.mdd.rest.impl.v1.representation.InstanceJSONRepresentation.SingleLink;
import com.abstratt.kirra.mdd.rest.impl.v1.representation.InstanceJSONRepresentationBuilder;
import com.abstratt.kirra.mdd.rest.impl.v1.representation.TupleParser;
import com.abstratt.mdd.frontend.web.JsonHelper;
import com.abstratt.mdd.frontend.web.ResourceUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

public abstract class AbstractKirraRepositoryResource extends ServerResource {

    /**
     * Returns this resource reference as an external reference.
     */
    public Reference getExternalReference() {
        return mapToExternal(getReference());
    }

    /**
     * Builds a JSON representation that comprises a list of instances.
     */
    protected Representation buildInstanceList(Entity targetEntity, List<Instance> instances) {
        List<InstanceJSONRepresentation> instanceReprs = new ArrayList<InstanceJSONRepresentation>();
        for (Instance instance : instances)
            instanceReprs.add(getInstanceJSONRepresentation(instance, targetEntity));
        return jsonToStringRepresentation(instanceReprs);
    }

    protected SingleLink buildLink(Instance instance) {
        SingleLink relValue = null;
        if (instance != null) {
            Entity relatedEntity = getRepository().getEntity(instance.getEntityNamespace(), instance.getEntityName());
            relValue = new SingleLink();
            String entitySegment = instance.getEntityNamespace() + '.' + instance.getEntityName();
            relValue.uri = getBaseReference().clone().addSegment("instances").addSegment(entitySegment).addSegment(instance.getObjectId())
                    .toUri().toString();
            relValue.shorthand = "" + instance.getValue(relatedEntity.getProperties().get(0).getName());
        }
        return relValue;
    }

    protected InstanceJSONRepresentation createInstanceJSONRepresentation() {
        return new InstanceJSONRepresentation();
    }

    @Override
    protected void doCatch(Throwable throwable) {
        if (throwable instanceof RuntimeException)
            // ensure errors bubble up out of the resource handler so
            // transaction is aborted
            throw (RuntimeException) throwable;
        super.doCatch(throwable);
    }

    protected <T> List<T> executeOperation(String entityNamespace, String entityName, String operationName, String objectId,
            boolean queryParameters) {
        Operation operation = findOperation(entityNamespace, entityName, operationName);
        ResourceUtils.ensure(operation != null, "Unknown operation: " + operationName, Status.CLIENT_ERROR_BAD_REQUEST);
        TupleParser tupleParser = new TupleParser(getRepository());

        try {
            List<Parameter> parameters = operation.getParameters();
            List<Object> arguments = new ArrayList<Object>(parameters.size());
            // decodes parameters from either query or entity
            if (queryParameters) {
                Form query = getQuery();
                for (int i = 0; i < parameters.size(); i++) {
                    String argument = query != null ? query.getFirstValue(parameters.get(i).getName()) : null;
                    arguments.add(argument);
                    ResourceUtils.ensure(argument != null || !parameters.get(i).isRequired(), "Parameter is required: "
                            + parameters.get(i).getName(), Status.CLIENT_ERROR_BAD_REQUEST);
                }
            } else {
                // parameters in entity
                JsonNode invocation = JsonHelper.parse(getRequestEntity().getReader());
                Iterator<String> argNames = invocation.fieldNames();
                Map<String, JsonNode> argumentMap = new HashMap<String, JsonNode>();
                while (argNames.hasNext()) {
                    String argName = argNames.next();
                    JsonNode argValueNode = invocation.get(argName);
                    argumentMap.put(argName, argValueNode);
                }
                for (Parameter parameter : parameters) {
                    ResourceUtils.ensure(argumentMap.containsKey(parameter.getName()) || !parameter.isRequired(), "Parameter is required: "
                            + parameter.getName(), Status.CLIENT_ERROR_BAD_REQUEST);
                    JsonNode argumentValueNode = argumentMap.get(parameter.getName());
                    Object argumentValue;
                    if (parameter.getTypeRef().getKind() == TypeKind.Entity)
                        argumentValue = tupleParser.resolveLink(argumentValueNode.textValue(), parameter.getTypeRef());
                    else
                        argumentValue = tupleParser.convertSlotValue(parameter.getTypeRef(), argumentValueNode);
                    arguments.add(argumentValue);
                }
            }
            String targetId = operation.isInstanceOperation() ? objectId : null;
            return (List<T>) getRepository().executeOperation(operation, targetId, arguments);
        } catch (JsonProcessingException e) {
            ResourceUtils.fail(e, null);
        } catch (IOException e) {
            ResourceUtils.fail(e, Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY);
        }
        // never gets here
        return null;
    }

    protected Operation findOperation(String entityNamespace, String entityName, String operationName) {
        try {
            Entity asEntity = getRepository().getEntity(entityNamespace, entityName);
            return asEntity.getOperation(operationName);
        } catch (KirraException e) {
            Service asService = getRepository().getService(entityNamespace, entityName);
            return asService.getOperation(operationName);
        }
    }

    /**
     * Returns a reference to the application base URI (up to the workspace
     * name).
     * 
     * @return
     */
    protected Reference getBaseReference() {
        return KirraReferenceUtils.getBaseReference(getRequest(), getReference());
    }

    protected Entity getEntityFromURI(String typeURI) {
        String entitySegment = new Reference(typeURI).getLastSegment();
        String[] fragments = StringUtils.split(entitySegment, '.');
        if (fragments.length != 2)
            return null;
        return getRepository().getEntity(fragments[0], fragments[1]);
    }

    protected String getEntityName() {
        return (String) getRequestAttributes().get("entityName");
    }

    protected String getEntityNamespace() {
        return (String) getRequestAttributes().get("entityNamespace");
    }

    /**
     * Builds a JSON representation for one instance. This representation may be
     * shown in isolation or as part of a list of instances.
     */
    protected InstanceJSONRepresentation getInstanceJSONRepresentation(Instance instance, Entity entity) {
        InstanceJSONRepresentation instanceRepr = createInstanceJSONRepresentation();
        new InstanceJSONRepresentationBuilder().build(instanceRepr, getReferenceBuilder(), entity, instance);
        return instanceRepr;
    }

    protected KirraReferenceBuilder getReferenceBuilder() {
        return new KirraReferenceBuilder(getAppReference());
    }

    protected Repository getRepository() {
        return KirraRESTUtils.getRepository();
    }

    protected Entity getTargetEntity() {
        String entityName = getEntityName();
        String entityNamespace = getEntityNamespace();
        return getRepository().getEntity(entityNamespace, entityName);
    }

    protected Representation jsonToStringRepresentation(Object jsonObject) {
        return KirraRESTUtils.jsonToStringRepresentation(jsonObject);
    }

    /**
     * Attempts to map the given internal reference to an external reference.
     */
    protected Reference mapToExternal(Reference reference) {
        return KirraReferenceUtils.mapToExternal(getRequest(), reference);
    }

    private Reference getAppReference() {
        return getBaseReference();
    }
}
