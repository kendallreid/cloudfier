package com.abstratt.mdd.core.tests.runtime;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.CoreException;

import com.abstratt.mdd.core.runtime.RuntimeObject;
import com.abstratt.mdd.core.runtime.RuntimeRaisedException;
import com.abstratt.mdd.core.runtime.types.BooleanType;
import com.abstratt.mdd.core.runtime.types.IntegerType;
import com.abstratt.mdd.core.runtime.types.StringType;

// abstract to prevent it being picked up for running
public abstract class OldRuntimeControlTests extends AbstractRuntimeTests {

    public static Test suite() {
        return new TestSuite(OldRuntimeControlTests.class);
    }

    public OldRuntimeControlTests(String name) {
        super(name);
    }


    public void testExceptionCapturing() throws CoreException {
        String source = "";
        source += "model tests;\n";
        source += "import mdd_types;\n";
        source += "class Simple\n";
        source += "static operation doIt() : Integer;\n";
        source += "begin\n";
        source += "  try \n";
        source += "      raise 2;\n";
        source += "      return 1;\n";
        source += "  catch (b : Integer)\n";
        source += "      return b;\n";
        source += "  end;\n";
        source += "end;\n";
        source += "end;\n";
        source += "end.";
        String[] sources = { source };
        parseAndCheck(sources);
        TestCase.assertEquals(new IntegerType(2), runStaticOperation("tests::Simple", "doIt"));
    }

    public void testExceptionHandling() throws CoreException {
        String source = "";
        source += "model tests;\n";
        source += "import mdd_types;\n";
        source += "class Simple\n";
        source += "static operation doIt() : Integer;\n";
        source += "begin\n";
        source += "  try \n";
        source += "      raise true;\n";
        source += "      return 1;\n";
        source += "  catch (b : Boolean)\n";
        source += "      return 2;\n";
        source += "  end;\n";
        source += "  return 3;\n";
        source += "end;\n";
        source += "end;\n";
        source += "end.";
        String[] sources = { source };
        parseAndCheck(sources);
        TestCase.assertEquals(new IntegerType(2), runStaticOperation("tests::Simple", "doIt"));
    }

    public void testPreconditionViolation() throws CoreException {
        String source = "";
        source += "model tests;\n";
        source += "import mdd_types;\n";
        source += "class Simple\n";
        source += "operation doIt() : Integer;\n";
        source += "precondition raises String { return true }\n";
        source += "precondition raises Boolean { return false }\n";
        source += "precondition raises Integer { return false }\n";
        source += "begin\n";
        source += "end;\n";
        source += "end;\n";
        source += "end.";
        String[] sources = { source };
        parseAndCheck(sources);
        final RuntimeObject[] targetObject = { null };
        runInRuntime(new Runnable() {
            @Override
            public void run() {
                targetObject[0] = newInstance("tests::Simple");
            }
        });
        try {
            runInRuntime(new Runnable() {
                @Override
                public void run() {
                    TestCase.fail("Should have failed " + runOperation(targetObject[0], "doIt"));
                }
            });
        } catch (RuntimeRaisedException e) {
            // expected
            TestCase.assertNotNull(e.getExceptionType());
            TestCase.assertEquals("mdd_types::Boolean", e.getExceptionType().getQualifiedName());
        }
    }

    public void testPropertyInvariantViolation() throws CoreException {
        String source = "";
        source += "model tests;\n";
        source += "import mdd_types;\n";
        source += "class Simple\n";
        source += "attribute attr1 : Integer := 10\n";
        source += "    invariant raises Integer { self.attr1 >= 10 }\n";
        source += "    invariant raises Boolean { self.attr1 <= 20 };\n";
        source += "attribute attr2 : String := \"Zoo\"\n";
        source += "    invariant { self.attr2 >= \"F\" };\n";
        source += "end;\n";
        source += "end.";
        String[] sources = { source };
        parseAndCheck(sources);

        final RuntimeObject[] targetObject = { null };
        targetObject[0] = newInstance("tests::Simple");
        writeAttribute(targetObject[0], "attr1", new IntegerType(20));
        try {
            writeAttribute(targetObject[0], "attr1", new IntegerType(5));
            TestCase.fail("Should have failed ");
        } catch (RuntimeRaisedException e) {
            // expected
            TestCase.assertNotNull(e.getExceptionType());
            TestCase.assertEquals("mdd_types::Integer", e.getExceptionType().getQualifiedName());
        }
        try {
            writeAttribute(targetObject[0], "attr1", new IntegerType(30));
            TestCase.fail("Should have failed ");
        } catch (RuntimeRaisedException e) {
            // expected
            TestCase.assertNotNull(e.getExceptionType());
            TestCase.assertEquals("mdd_types::Boolean", e.getExceptionType().getQualifiedName());
        }
        TestCase.assertEquals(new IntegerType(20), readAttribute(targetObject[0], "attr1"));

        writeAttribute(targetObject[0], "attr2", new StringType("Foo"));

        try {
            writeAttribute(targetObject[0], "attr2", new StringType("Bar"));
            TestCase.fail("Should have failed ");
        } catch (RuntimeRaisedException e) {
            // expected
            TestCase.assertNull(e.getExceptionType());
        }
    }

    public void testPropertyInvariantViolationOnAssociationEnd() throws CoreException {
        String source = "";
        source += "model tests;\n";
        source += "import mdd_types;\n";
        source += "class Simple1\n";
        source += "attribute s2 : Simple2[*] invariant { return self.s2.size() < 3 };\n";
        source += "end;\n";
        source += "class Simple2\n";
        source += "attribute s1 : Simple1[*];\n";
        source += "end;\n";
        source += "association Assoc\n";
        source += "role Simple1.s2;\n";
        source += "role Simple2.s1;\n";
        source += "end;\n";
        source += "end.";
        String[] sources = { source };
        parseAndCheck(sources);

        final RuntimeObject[] s1 = { null };
        final RuntimeObject[] s2 = { null, null, null };

        runInRuntime(new Runnable() {
            @Override
            public void run() {
                s1[0] = newInstance("tests::Simple1");
                s2[0] = newInstance("tests::Simple2");
                s2[1] = newInstance("tests::Simple2");
                s2[2] = newInstance("tests::Simple2");
                s1[0].link(getProperty("tests::Simple1::s2"), s2[0]);
                s1[0].link(getProperty("tests::Simple1::s2"), s2[1]);
            }
        });

        try {
            runInRuntime(new Runnable() {
                @Override
                public void run() {
                    s1[0].link(getProperty("tests::Simple1::s2"), s2[2]);
                }
            });
            TestCase.fail("Should have failed");
        } catch (RuntimeRaisedException rre) {
            // expected
        }
    }

    public void testRollbackOnException() throws CoreException {
        String source = "";
        source += "model tests;\n";
        source += "import mdd_types;\n";
        source += "class Simple\n";
        source += "attribute attr1 : Integer := 1;\n";
        source += "operation changeAttr1(newAttr1 : Integer, shouldFail : Boolean) raises String;\n";
        source += "begin\n";
        source += "    self.attr1 := newAttr1;\n";
        source += "    if (shouldFail) then\n";
        source += "        raise \"\";\n";
        source += "end;\n";
        source += "end;\n";
        source += "end.";
        String[] sources = { source };
        parseAndCheck(sources);

        final RuntimeObject[] person = { null };
        runInRuntime(new Runnable() {
            @Override
            public void run() {
                person[0] = newInstance("tests::Simple");
            }
        });

        TestCase.assertEquals(new IntegerType(1), readAttribute(person[0], "attr1"));

        runInRuntime(new Runnable() {
            @Override
            public void run() {
                runOperation(person[0], "changeAttr1", new IntegerType(2), BooleanType.fromValue(false));
            }
        });
        TestCase.assertEquals("should have changed", new IntegerType(2), readAttribute(person[0], "attr1"));

        try {
            runInRuntime(new Runnable() {
                @Override
                public void run() {
                    runOperation(person[0], "changeAttr1", new IntegerType(3), BooleanType.fromValue(true));
                }
            });
            TestCase.fail("Should have failed");
        } catch (RuntimeRaisedException e) {
            // expected
        }
        // should not have changed
        TestCase.assertEquals("should NOT have changed", new IntegerType(2), readAttribute(person[0], "attr1"));
    }
}
