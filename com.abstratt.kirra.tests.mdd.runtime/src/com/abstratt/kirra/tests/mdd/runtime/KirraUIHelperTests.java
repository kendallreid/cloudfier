package com.abstratt.kirra.tests.mdd.runtime;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.UMLPackage;

import com.abstratt.kirra.mdd.core.KirraHelper;
import com.abstratt.kirra.mdd.ui.KirraUIHelper;
import com.abstratt.mdd.core.IRepository;
import com.abstratt.mdd.core.tests.harness.AbstractRepositoryBuildingTests;

public class KirraUIHelperTests extends AbstractRepositoryBuildingTests {

    public KirraUIHelperTests(String name) {
        super(name);
    }
    
    @Override
    public void setUp() throws Exception {
    	super.setUp();
    }
    
    @Override
    protected void tearDown() throws Exception {
    	super.tearDown();
    }

    public void testIsEditable() throws CoreException {
    	String model = "";
        model += "package mypackage;\n";
        model += "import base;\n";
        model += "apply kirra;\n";
        model += "class MyClass\n";
        model += "  attribute attr1 : String[0,1];\n";
        model += "  readonly attribute attr2 : String[0,1];\n";
        model += "  readonly attribute attr3 : String[0,1] := \"Initial\";\n";
        model += "  attribute attr4 : String;\n";
        model += "  readonly attribute attr5 : String;\n";
        model += "  readonly attribute attr6 : String := \"Initial\";\n";        
        model += "end;\n";
        model += "end.";

        parseAndCheck(model);
        
        assertTrue(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr1"), false));
        assertTrue(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr1"), true));
        assertFalse(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr2"), false));
        assertFalse(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr2"), true));
        assertFalse(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr3"), false));
        assertFalse(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr3"), true));
        assertTrue(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr4"), false));
        assertTrue(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr4"), true));
        assertFalse(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr5"), false));
        assertTrue(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr5"), true));
        assertFalse(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr6"), false));
        assertTrue(KirraUIHelper.isEditable(getProperty("mypackage::MyClass::attr6"), true));
    }
    
    public void testGetUserNameProperty() throws CoreException {
    	String model1 = "";
        model1 += "package mypackage1;\n";
        model1 += "import base;\n";
        model1 += "apply kirra;\n";
        model1 += "class MyClass\n";
        model1 += "  attribute attr1 : String;\n";
        model1 += "end;\n";
        model1 += "[kirra::User] class MyClass2\n";
        model1 += "  attribute attr2 : String;\n";
        model1 += "end;\n";
        model1 += "end.\n";
        String model2 = "";
        model2 += "package mypackage2;\n";
        model2 += "import base;\n";
        model2 += "apply kirra;\n";
        model2 += "[kirra::User] class MyClass3\n";
        model2 += "  attribute attr3 : String;\n";
        model2 += "end;\n";
        model2 += "class MyClass4\n";
        model2 += "  attribute attr4 : String;\n";
        model2 += "end;\n";        
        model2 += "end.";

        parseAndCheck(model1, model2);
        
        Package pkg1 = get("mypackage1", UMLPackage.Literals.PACKAGE);
        Package pkg2 = get("mypackage2", UMLPackage.Literals.PACKAGE);
		List<Class> userEntities = KirraUIHelper.getUserEntities(Arrays.asList(pkg1, pkg2));
		assertEquals(2, userEntities.size());
		assertTrue(userEntities.contains(getClass("mypackage1::MyClass2")));
		assertTrue(userEntities.contains(getClass("mypackage2::MyClass3")));
    }
    
    public void testIsChildTabRelationship_unidirectionalManyToMany() throws CoreException {
    	String model = "";
        model += "package shipit;\n";
        model += "import base;\n";
        model += "apply kirra;\n";
        model += "class Issue\n";
        model += "  attribute summary : String;\n";
        model += "  attribute labels : Label[*];\n";
        model += "end;\n";
        model += "class Label\n";
        model += "  attribute text : String;\n";
        model += "  attribute labels : Label[*];\n";
        model += "end;\n";
        model += "association IssueLabels\n";
        model += "  role Issue.labels;\n";
        model += "  role labeled : Issue[*];\n";
        model += "end;\n";        
        model += "end.";

        parseAndCheck(model);
        
        assertTrue(KirraUIHelper.isRelationship(getProperty("shipit::Issue::labels")));
        assertTrue(KirraUIHelper.isChildTabRelationship(getProperty("shipit::Issue::labels")));
    }

    public void testIsFormField() throws CoreException {
    	String model = "";
        model += "package shipit;\n";
        model += "import base;\n";
        model += "apply kirra;\n";
        model += "class Issue\n";
        model += "  attribute summary : String;\n";
        model += "  readonly attribute reporter : User;\n";
        model += "end;\n";
        model += "class User\n";
        model += "  attribute name : String;\n";
        model += "end;\n";
        model += "end.";

        parseAndCheck(model);
        
        assertTrue(KirraUIHelper.getFormFields(getClass("shipit::Issue")).contains(getProperty("shipit::Issue::reporter")));
        assertTrue(KirraUIHelper.isFormField(getProperty("shipit::Issue::reporter")));
        assertTrue(KirraUIHelper.isEditableFormField(getProperty("shipit::Issue::reporter"), true));
        assertFalse(KirraUIHelper.isEditableFormField(getProperty("shipit::Issue::reporter"), false));
    }

	@Override
	protected Properties createDefaultSettings() {
		Properties defaultSettings = super.createDefaultSettings();
		defaultSettings.setProperty(IRepository.EXTEND_BASE_OBJECT, Boolean.TRUE.toString());
		defaultSettings.setProperty("mdd.enableKirra", Boolean.TRUE.toString());
		defaultSettings.setProperty("mdd.modelWeaver", "kirraWeaver");
		return defaultSettings;
	}
}
