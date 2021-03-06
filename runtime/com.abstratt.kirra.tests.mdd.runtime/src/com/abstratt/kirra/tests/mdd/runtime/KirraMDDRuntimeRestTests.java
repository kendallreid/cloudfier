package com.abstratt.kirra.tests.mdd.runtime;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import junit.framework.TestCase;

import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.eclipse.core.runtime.CoreException;
import org.osgi.framework.ServiceRegistration;

import com.abstratt.kirra.Instance;
import com.abstratt.kirra.Operation;
import com.abstratt.kirra.Repository;
import com.abstratt.kirra.auth.AuthenticationService;
import com.abstratt.mdd.core.RepositoryService;
import com.abstratt.mdd.frontend.web.JsonHelper;
import com.abstratt.resman.Resource;
import com.abstratt.resman.Task;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class KirraMDDRuntimeRestTests extends AbstractKirraRestV1Tests {

    Map<String, String> authorized = new HashMap<String, String>();
    
    private JsonNodeFactory jsonNodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    protected ServiceRegistration<AuthenticationService> authenticatorRegistration;
    
    public KirraMDDRuntimeRestTests(String name) {
        super(name);
    }

    public void testAction() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User\n";
        model += "    readonly id attribute username : String;\n";
        model += "end;\n";
        model += "class MyClass1\n";
        model += "    attribute attr1 : Integer;\n";
        model += "    operation add(value : Integer);\n";
        model += "    begin\n";
        model += "        self.attr1 := self.attr1 + value;\n";
        model += "    end;\n";
        model += "end;\n";
        model += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);
        URI sessionURI = getWorkspaceBaseURI();
        final Instance created = RepositoryService.DEFAULT.runTask(getRepositoryURI(), new Task<Instance>() {
            @Override
            public Instance run(Resource<?> resource) {
                Repository repository = resource.getFeature(Repository.class);
                Instance fresh = repository.newInstance("mypackage", "MyClass1");
                fresh.setValue("attr1", 10);
                return repository.createInstance(fresh);
            }
        });

        PostMethod post = new PostMethod(sessionURI.resolve("instances/mypackage.MyClass1/" + created.getObjectId() + "/actions/add")
                .toString());
        post.setRequestEntity(new StringRequestEntity("{value: 15}", "application/json", "UTF-8"));
        ObjectNode result = (ObjectNode) executeJsonMethod(200, post);

        TestCase.assertEquals(25, result.path("values").path("attr1").intValue());

        RepositoryService.DEFAULT.runTask(getRepositoryURI(), new Task<Object>() {
            @Override
            public Object run(Resource<?> resource) {
                Repository repository = resource.getFeature(Repository.class);
                Instance reloaded = repository.getInstance(created.getEntityNamespace(), created.getEntityName(), created.getObjectId(),
                        false);
                TestCase.assertEquals(25L, reloaded.getValue("attr1"));
                return null;
            }
        });
    }

    //TODO-RC disabled until we reimplement authentication
    public void _testBadLogin() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User id readonly attribute attr1 : String; end;\n";
        model += "end.";
        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), false);

        URI sessionURI = getWorkspaceBaseURI();
        PostMethod login = new PostMethod(sessionURI.resolve("login").toString());
        login.setRequestEntity(new StringRequestEntity("login=unknownuser&password=pass", "application/x-www-form-urlencoded", "UTF-8"));
        executeMethod(401, login);
    }

    public void testCreateInstance() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User\n";
        model += "    readonly id attribute username : String;\n";
        model += "end;\n";
        model += "class MyClass1\n";
        model += "    attribute attr1 : String;\n";
        model += "    attribute attr2 : Integer := 5;\n";
        model += "    attribute attr3 : Integer := 90;\n";
        model += "    attribute attr4 : Integer[0,1] := 80;\n";
        model += "    attribute attr5 : Integer[0,1];\n";
        model += "end;\n";
        model += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);
        URI sessionURI = getWorkspaceBaseURI();

        GetMethod getTemplateInstance = new GetMethod(sessionURI.resolve("instances/mypackage.MyClass1/_template").toASCIIString());

        ObjectNode template = (ObjectNode) executeJsonMethod(200, getTemplateInstance);

        ((ObjectNode) template.get("values")).put("attr1", "foo");
        ((ObjectNode) template.get("values")).put("attr3", 100);
        ((ObjectNode) template.get("values")).put("attr4", (String) null);
        PostMethod createMethod = new PostMethod(sessionURI.resolve("instances/mypackage.MyClass1/").toString());
        createMethod.setRequestEntity(new StringRequestEntity(template.toString(), "application/json", "UTF-8"));

        ObjectNode created = (ObjectNode) executeJsonMethod(201, createMethod);
        TestCase.assertEquals("foo", created.get("values").get("attr1").asText());
        TestCase.assertEquals(5, created.get("values").get("attr2").asLong());
        TestCase.assertEquals(100, created.get("values").get("attr3").asLong());
        TestCase.assertNull(created.get("values").get("attr4"));
        TestCase.assertNull(created.get("values").get("attr5"));
    }

    /**
     * Ensures after a profile is created we can see the the current user in the
     * application index.
     */
    //TODO-RC disabled until we reimplement authentication
    public void _testCreateProfile() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User\n";
        model += "    readonly id attribute username : String;\n";
        model += "    static derived attribute current : User := { (System#user() as User) };\n";
        model += "end;\n";
        model += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), false);
        URI sessionURI = getWorkspaceBaseURI();

        signUp(getName(), "pass");
        login(getName(), "pass");

        String typeURI = sessionURI.resolve("entities/mypackage.User").toString();
        String requestMarkup = "{'type': '" + typeURI + "'}";

        PostMethod createMethod = new PostMethod(sessionURI.resolve("profile").toString());
        createMethod.setRequestEntity(new StringRequestEntity(requestMarkup, "application/json", "UTF-8"));
        ObjectNode created = (ObjectNode) executeJsonMethod(201, createMethod);

        TestCase.assertEquals(typeURI, created.get("type").asText());

        // the root should be pointing to the user just created
        GetMethod getApplicationRoot = new GetMethod(sessionURI.toASCIIString());
        ObjectNode root = (ObjectNode) executeJsonMethod(200, getApplicationRoot);
        JsonNode profileURI = root.path("currentUser").path("profile").path("uri");
        TestCase.assertNotNull(profileURI);
        TestCase.assertEquals(created.get("uri").asText(), profileURI.asText());
    }

    public void testDeleteInstance() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User id readonly attribute attr1 : String; end;\n";
        model += "class MyClass1 attribute attr1 : String[0,1]; end;\n";
        model += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);
        URI sessionURI = getWorkspaceBaseURI();

        final Instance created = RepositoryService.DEFAULT.runTask(getRepositoryURI(), new Task<Instance>() {
            @Override
            public Instance run(Resource<?> resource) {
                Repository repository = resource.getFeature(Repository.class);
                Instance created = repository.createInstance(repository.newInstance("mypackage", "MyClass1"));

                TestCase.assertNotNull(repository.getInstance("mypackage", "MyClass1", created.getObjectId(), false));
                return created;
            }
        });

        DeleteMethod delete = new DeleteMethod(sessionURI.resolve("instances/mypackage.MyClass1/" + created.getObjectId()).toASCIIString());
        executeMethod(204, delete);

        RepositoryService.DEFAULT.runTask(getRepositoryURI(), new Task<Object>() {
            @Override
            public Object run(Resource<?> resource) {
                Repository repository = resource.getFeature(Repository.class);
                TestCase.assertNull(repository.getInstance("mypackage", "MyClass1", created.getObjectId(), false));
                return null;
            }
        });

        executeMethod(404, delete);
    }

    public void testGetEntity() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User\n";
        model += "    readonly id attribute username : String;\n";
        model += "end;\n";
        model += "class MyClass1\n";
        model += "    attribute attr1 : String;\n";
        model += "    attribute attr2 : Integer;\n";
        model += "    operation op1();\n";
        model += "    operation op2();\n";
        model += "    static query op3() : MyClass1;\n";
        model += "end;\n";
        model += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);

        URI sessionURI = getWorkspaceBaseURI();
        String entityUri = sessionURI.resolve("entities/").resolve("mypackage.MyClass1").toASCIIString();
        GetMethod getEntity = new GetMethod(entityUri);
        executeMethod(200, getEntity);

        ObjectNode jsonEntity = (ObjectNode) JsonHelper.parse(new InputStreamReader(getEntity.getResponseBodyAsStream()));

        TestCase.assertEquals("MyClass1", jsonEntity.get("name").textValue());
        TestCase.assertEquals("mypackage", jsonEntity.get("namespace").textValue());
        TestCase.assertEquals(entityUri, jsonEntity.get("uri").textValue());

        TestCase.assertNotNull(jsonEntity.get("instances"));

        TestCase.assertEquals(this.restHelper.getApiUri().resolve("instances/mypackage.MyClass1").toString(), jsonEntity.get("instances")
                .asText());

        TestCase.assertEquals(Arrays.asList("op1", "op2"), jsonEntity.get("actions").findValuesAsText("name"));
        TestCase.assertEquals(Arrays.asList("op3"), jsonEntity.get("finders").findValuesAsText("name"));
        TestCase.assertEquals(Arrays.asList("attr1", "attr2"), jsonEntity.get("properties").findValuesAsText("name"));
        TestCase.assertEquals(Arrays.asList("String", "Integer"), jsonEntity.get("properties").findValuesAsText("type"));
    }

    public void testGetEntityList() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "class MyClass1 attribute a : String; end;\n";
        model += "class MyClass2 attribute a : Integer; end;\n";
        model += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);
        URI sessionURI = getWorkspaceBaseURI();
        GetMethod getDomainTypes = new GetMethod(sessionURI.resolve("entities/").toASCIIString());
        executeMethod(200, getDomainTypes);

        ArrayNode entities = (ArrayNode) JsonHelper.parse(new InputStreamReader(getDomainTypes.getResponseBodyAsStream()));
        TestCase.assertEquals(3, entities.size());

        
        List<Object> elementList = IteratorUtils.toList(entities.elements(), entities.size());
		List<JsonNode> myPackageEntities = elementList.stream().map(it -> ((JsonNode) it)).filter(it -> "mypackage".equals(it.get("namespace").textValue())).collect(Collectors.toList());
		assertEquals(2, myPackageEntities.size());
		TestCase.assertEquals("MyClass1", myPackageEntities.get(0).get("name").textValue());
		TestCase.assertEquals("MyClass2", myPackageEntities.get(1).get("name").textValue());
		
        for (JsonNode jsonNode : myPackageEntities) {
            TestCase.assertEquals("mypackage", jsonNode.get("namespace").textValue());
            TestCase.assertNotNull(jsonNode.get("uri"));
            executeMethod(200, new GetMethod(jsonNode.get("uri").toString()));
        }
    }

    public void testGetInstance() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User\n";
        model += "    readonly id attribute username : String;\n";
        model += "end;\n";
        model += "class MyClass1\n";
        model += "    attribute attr1 : String;\n";
        model += "    attribute attr2 : Integer;\n";
        model += "end;\n";
        model += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);
        URI sessionURI = getWorkspaceBaseURI();

        Instance created = RepositoryService.DEFAULT.runTask(getRepositoryURI(), new Task<Instance>() {
            @Override
            public Instance run(Resource<?> resource) {
                Repository repository = resource.getFeature(Repository.class);
                Instance instance = repository.newInstance("mypackage", "MyClass1");
                instance.setValue("attr1", "The answer is");
                instance.setValue("attr2", "42");
                Instance created = repository.createInstance(instance);
                return created;
            }
        });

        ObjectNode jsonInstance = executeJsonMethod(200,
                new GetMethod(sessionURI.resolve("instances/mypackage.MyClass1/" + created.getObjectId()).toASCIIString()));

        TestCase.assertNotNull(jsonInstance.get("uri"));
        executeMethod(200, new GetMethod(jsonInstance.get("uri").toString()));
        TestCase.assertNotNull(jsonInstance.get("type"));
        executeMethod(200, new GetMethod(jsonInstance.get("type").toString()));

        ObjectNode values = (ObjectNode) jsonInstance.get("values");
        TestCase.assertEquals("The answer is", values.get("attr1").textValue());
        TestCase.assertEquals(42L, values.get("attr2").asLong());
    }

    public void testGetInstanceList() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User\n";
        model += "    readonly id attribute username : String;\n";
        model += "end;\n";
        model += "class MyClass1 attribute a : Integer[0,1]; end;\n";
        model += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);

        RepositoryService.DEFAULT.runTask(getRepositoryURI(), new Task<Object>() {
            @Override
            public Object run(Resource<?> resource) {
                Repository repository = resource.getFeature(Repository.class);
                repository.createInstance(repository.newInstance("mypackage", "MyClass1"));
                repository.createInstance(repository.newInstance("mypackage", "MyClass1"));
                return null;
            }
        });

        URI sessionURI = getWorkspaceBaseURI();

        GetMethod getInstances = new GetMethod(sessionURI.resolve("instances/mypackage.MyClass1/").toASCIIString());
        executeMethod(200, getInstances);

        ArrayNode instances = (ArrayNode) JsonHelper.parse(new InputStreamReader(getInstances.getResponseBodyAsStream()));
        TestCase.assertEquals(2, instances.size());

        for (JsonNode jsonNode : instances) {
            TestCase.assertNotNull(jsonNode.get("uri"));
            executeMethod(200, new GetMethod(jsonNode.get("uri").toString()));
            TestCase.assertNotNull(jsonNode.get("type"));
            executeMethod(200, new GetMethod(jsonNode.get("type").toString()));
        }
    }

    public void testUpdateInstance() throws CoreException, IOException {
        List<Instance> created = testUpdateInstanceSetup();
        URI sessionURI = getWorkspaceBaseURI();
        
        ObjectNode jsonInstance1 = executeJsonMethod(200,
                new GetMethod(sessionURI.resolve("instances/mypackage.MyClass1/" + created.get(0).getObjectId()).toASCIIString()));
        
        ((ObjectNode) jsonInstance1.get("values")).set("attr1", new TextNode("value 1a"));
        ((ObjectNode) jsonInstance1.get("values")).set("attr2", new TextNode("value 2a"));

        PutMethod putMethod = new PutMethod(jsonInstance1.get("uri").textValue());
        putMethod.setRequestEntity(new StringRequestEntity(jsonInstance1.toString(), "application/json", "UTF-8"));

        ObjectNode updated = (ObjectNode) executeJsonMethod(200, putMethod);
        TestCase.assertEquals("value 1a", updated.get("values").get("attr1").asText());
        TestCase.assertEquals("value 2a", updated.get("values").get("attr2").asText());
        
        ObjectNode retrieved = executeJsonMethod(200,
                new GetMethod(jsonInstance1.get("uri").textValue()));
        TestCase.assertEquals("value 1a", retrieved.get("values").get("attr1").asText());
        TestCase.assertEquals("value 2a", retrieved.get("values").get("attr2").asText());
    }
    
    public void testUpdateInstance_ClearValue() throws CoreException, IOException {
        List<Instance> created = testUpdateInstanceSetup();
        URI sessionURI = getWorkspaceBaseURI();
        
        ObjectNode jsonInstance1 = executeJsonMethod(200,
                new GetMethod(sessionURI.resolve("instances/mypackage.MyClass1/" + created.get(0).getObjectId()).toASCIIString()));
        
        ((ObjectNode) jsonInstance1.get("values")).set("attr1", new TextNode(""));

        PutMethod putMethod = new PutMethod(jsonInstance1.get("uri").textValue());
        putMethod.setRequestEntity(new StringRequestEntity(jsonInstance1.toString(), "application/json", "UTF-8"));

        ObjectNode updated = (ObjectNode) executeJsonMethod(200, putMethod);
        TestCase.assertEquals("", updated.get("values").get("attr1").asText());
        
        ObjectNode retrieved = executeJsonMethod(200,
                new GetMethod(jsonInstance1.get("uri").textValue()));
        TestCase.assertEquals("", retrieved.get("values").get("attr1").asText());
    }
    
    public void testUpdateInstance_SetLink() throws CoreException, IOException {
        List<Instance> created = testUpdateInstanceSetup();
        URI sessionURI = getWorkspaceBaseURI();
        URI uri1 = sessionURI.resolve("instances/mypackage.MyClass1/" + created.get(0).getObjectId());
        URI uri2 = sessionURI.resolve("instances/mypackage.MyClass2/" + created.get(1).getObjectId());
        
        ObjectNode jsonInstance1 = executeJsonMethod(200,
                new GetMethod(uri1.toString()));
        ObjectNode myClass2 = jsonNodeFactory.objectNode();
        ((ObjectNode) jsonInstance1.get("values")).put("myClass2", myClass2);  
        myClass2.set("uri", new TextNode(uri2.toString()));

        PutMethod putMethod = new PutMethod(uri1.toString());
        putMethod.setRequestEntity(new StringRequestEntity(jsonInstance1.toString(), "application/json", "UTF-8"));

        ObjectNode updated = (ObjectNode) executeJsonMethod(200, putMethod);
        assertNotNull(updated.get("values").get("myClass2"));
        TestCase.assertEquals(uri2.toString(), updated.get("values").get("myClass2").get("uri").asText());
        
        ObjectNode retrieved = executeJsonMethod(200,
                new GetMethod(jsonInstance1.get("uri").textValue()));
        assertNotNull(retrieved.get("values"));
        assertNotNull(retrieved.get("values").get("myClass2"));
        TestCase.assertEquals(uri2.toString(), retrieved.get("values").get("myClass2").get("uri").asText());
    }    

	private List<Instance> testUpdateInstanceSetup() throws IOException, CoreException {
		String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "class MyClass1\n";
        model += "    attribute attr1 : String[0,1];\n";
        model += "    attribute attr2 : String[0,1];\n";
        model += "    attribute myClass2 : MyClass2[0,1];\n";
        model += "end;\n";
        model += "class MyClass2\n";
        model += "    attribute attr2 : String[0,1];\n";
        model += "end;\n";
        model += "end.";
        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);
        return RepositoryService.DEFAULT.runTask(getRepositoryURI(), new Task<List<Instance>>() {
        	@Override
        	public List<Instance> run(Resource<?> resource) {
        		List<Instance> created = new LinkedList<>();
        		Repository repository = resource.getFeature(Repository.class);
        		Instance instance1 = repository.newInstance("mypackage", "MyClass1");
        		instance1.setValue("attr1", "value1");
        		created.add(repository.createInstance(instance1));
        		Instance instance2 = repository.newInstance("mypackage", "MyClass2");
        		instance2.setValue("attr2", "value2");
        		created.add(repository.createInstance(instance2));
        		return created;
        	}
        });
	}

    
    public void testGetServiceList() throws CoreException, IOException {
        String source = "";
        source += "model tests;\n";
        source += "interface Calculator\n";
        source += "    operation addNumbers(number1 : Integer, number2 : Integer) : Integer;\n";
        source += "end;\n";
        source += "external class CalculatorService implements Calculator\n";
        source += "end;\n";
        source += "component CalculatorComponent\n";
        source += "    composition calculatorService : CalculatorService;\n";
        source += "    provided port calculator : Calculator connector calculatorService;\n";
        source += "end;\n";
        source += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", source.getBytes()), true);

        URI sessionURI = getWorkspaceBaseURI();
        GetMethod getDomainTypes = new GetMethod(sessionURI.resolve("services/").toASCIIString());
        executeMethod(200, getDomainTypes);

        ArrayNode entities = (ArrayNode) JsonHelper.parse(new InputStreamReader(getDomainTypes.getResponseBodyAsStream()));
        TestCase.assertEquals(1, entities.size());

        TestCase.assertEquals("CalculatorService", entities.get(0).get("name").textValue());
        TestCase.assertEquals("tests", entities.get(0).get("namespace").textValue());
        TestCase.assertNotNull(entities.get(0).get("uri"));
        executeMethod(200, new GetMethod(entities.get(0).get("uri").toString()));
    }

    public void testGetTemplateInstance() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User\n";
        model += "    readonly id attribute username : String;\n";
        model += "end;\n";
        model += "class MyClass1\n";
        model += "    attribute attr1 : String;\n";
        model += "    attribute attr2 : Integer := 5;\n";
        model += "end;\n";
        model += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);
        URI sessionURI = getWorkspaceBaseURI();

        GetMethod getTemplateInstance = new GetMethod(sessionURI.resolve("instances/mypackage.MyClass1/_template").toASCIIString());
        executeMethod(200, getTemplateInstance);

        ObjectNode jsonInstance = (ObjectNode) JsonHelper.parse(new InputStreamReader(getTemplateInstance.getResponseBodyAsStream()));

        TestCase.assertTrue(jsonInstance.get("uri").isNull());
        TestCase.assertFalse(NullNode.instance.equals(jsonInstance.get("type")));
        TestCase.assertNotNull(jsonInstance.get("type"));
        executeMethod(200, new GetMethod(jsonInstance.get("type").toString()));

        TestCase.assertNull(((ObjectNode) jsonInstance.get("values")).get("attr1"));
        TestCase.assertEquals(5, ((ObjectNode) jsonInstance.get("values")).get("attr2").asLong());
    }

    public void testNewSession() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "import base;\n";
        model += "apply kirra;\n";
        model += "class MyClass\n";
        model += "    readonly id attribute name : String;\n";
        model += "end;\n";
        model += "end.";
        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);
        RepositoryService.DEFAULT.runTask(getRepositoryURI(), new Task<Object>() {
            @Override
            public Object run(Resource<?> resource) {
                Repository repository = resource.getFeature(Repository.class);
                TestCase.assertTrue(repository.isOpen());
                TestCase.assertEquals(1, repository.getEntities("mypackage").size());
                TestCase.assertEquals("MyClass", repository.getEntities("mypackage").get(0).getName());
                return null;
            }
        });
    }

    public void testQuery() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User\n";
        model += "    readonly id attribute username : String;\n";
        model += "end;\n";
        model += "class MyClass1\n";
        model += "    attribute attr1 : Integer;\n";
        model += "    static query findAttr1GreaterThan(value : Integer) : MyClass1[*];\n";
        model += "    begin\n";
        model += "        return MyClass1 extent.select((a : MyClass1) : Boolean { a.attr1 > value });\n";
        model += "    end;\n";
        model += "end;\n";
        model += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), true);
        URI sessionURI = getWorkspaceBaseURI();

        RepositoryService.DEFAULT.runTask(getRepositoryURI(), new Task<Object>() {
            @Override
            public Object run(Resource<?> resource) {
                Repository repository = resource.getFeature(Repository.class);
                Instance instance1 = repository.newInstance("mypackage", "MyClass1");
                instance1.setValue("attr1", 10);
                Instance instance2 = repository.newInstance("mypackage", "MyClass1");
                instance2.setValue("attr1", 20);
                Instance instance3 = repository.newInstance("mypackage", "MyClass1");
                instance3.setValue("attr1", 30);
                repository.createInstance(instance1);
                repository.createInstance(instance2);
                repository.createInstance(instance3);
                return null;
            }
        });

        PostMethod queryMethod = new PostMethod(sessionURI.resolve("finders/mypackage.MyClass1/findAttr1GreaterThan").toString());
        queryMethod.setRequestEntity(new StringRequestEntity("{value: 15}", "application/json", "UTF-8"));

        ArrayNode instances = (ArrayNode) executeJsonMethod(200, queryMethod);
        TestCase.assertEquals(2, instances.size());

        for (JsonNode jsonNode : instances) {
            TestCase.assertNotNull(jsonNode.get("uri"));
            TestCase.assertNotNull(jsonNode.get("type"));
            executeMethod(200, new GetMethod(jsonNode.get("type").toString()));
            JsonNode instance = executeJsonMethod(200, new GetMethod(jsonNode.get("uri").toString()));
            TestCase.assertTrue(instance.get("values").get("attr1").longValue() > 15);
        }

        queryMethod.setRequestEntity(new StringRequestEntity("{value: 20}", "application/json", "UTF-8"));

        instances = (ArrayNode) executeJsonMethod(200, queryMethod);
        TestCase.assertEquals(1, instances.size());
        JsonNode instance = executeJsonMethod(200, new GetMethod(instances.get(0).get("uri").toString()));
        TestCase.assertEquals(30, instance.get("values").get("attr1").longValue());
    }

    /**
     * Shows how a service that provides data can be accessed.
     */
    public void testServiceDataAccess() throws CoreException, IOException {
        String source = "";
        source += "model tests;\n";
        source += "interface Calculator\n";
        source += "    operation addNumbers(number1 : Integer, number2 : Integer) : Integer;\n";
        source += "end;\n";
        source += "class CalculatorService implements Calculator\n";
        source += "    operation addNumbers(number1 : Integer, number2 : Integer) : Integer;\n";
        source += "    begin\n";
        source += "        return number1 + number2;\n";
        source += "    end;\n";
        source += "end;\n";
        source += "component CalculatorComponent\n";
        source += "    composition calculatorService : CalculatorService;\n";
        source += "    provided port calculator : Calculator connector calculatorService;\n";
        source += "end;\n";
        source += "end.";

        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", source.getBytes()), true);
        URI sessionURI = getWorkspaceBaseURI();

        RepositoryService.DEFAULT.runTask(getRepositoryURI(), new Task<Object>() {
            @Override
            public Object run(Resource<?> resource) {
                Repository repository = resource.getFeature(Repository.class);
                Operation operation = repository.getService("tests", "CalculatorService").getOperation("addNumbers");
                List<?> result = repository.executeOperation(operation, null, Arrays.asList(31, 11));
                TestCase.assertEquals(1, result.size());
                TestCase.assertEquals(42l, result.get(0));
                return null;
            }
        });

        GetMethod queryMethod = new GetMethod(sessionURI.resolve("retrievers/tests.CalculatorService/addNumbers?number1=31&number2=11")
                .toString());
        JsonNode jsonResult = executeJsonMethod(200, queryMethod);
        TestCase.assertNotNull(jsonResult.get("data"));
        ArrayNode asArray = (ArrayNode) jsonResult.get("data");
        TestCase.assertEquals(1, asArray.size());
        TestCase.assertEquals(42L, asArray.get(0).longValue());
    }

    public void testSignup() throws CoreException, IOException {
        String model = "";
        model += "package mypackage;\n";
        model += "apply kirra;\n";
        model += "import base;\n";
        model += "role class User readonly id attribute attr1 : String; end;\n";
        model += "end.";
        buildProjectAndLoadRepository(Collections.singletonMap("test.tuml", model.getBytes()), false);

        final String username = getName() + "@foo.com";

        // first sign up should work
        signUp(username, "pass", 204);

        // double sign up should fail
        signUp(username, "pass", 400);

        // can login
        login(username, "pass");
    }

    @Override
    protected void tearDown() throws Exception {
        if (authenticatorRegistration != null)
            authenticatorRegistration.unregister();
        super.tearDown();
    }
}
