package com.abstratt.mdd.core.tests.runtime;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.CoreException;

import com.abstratt.mdd.core.runtime.RuntimeObject;
import com.abstratt.mdd.core.runtime.types.BooleanType;
import com.abstratt.mdd.core.runtime.types.IntegerType;
import com.abstratt.mdd.core.runtime.types.RealType;

public class PrimitiveActionTests extends AbstractRuntimeTests {

	private static String structure;

	static {
		structure = "";
		structure += "model tests;\n";
		structure += "import base;\n";
		structure += "class Simple\n";
		structure += "attribute integerAttribute1 : Integer;\n";
		structure += "derived attribute doubleIntegerAttribute1 : Integer := { 2.multiply(self.integerAttribute1) };\n";
		structure += "operation operateOnInteger(value :  Integer) : Integer;\n";
		structure += "operation getANumber() : Integer;\n";
		structure += "operation getABoolean() : Boolean;\n";
		structure += "static readonly attribute INTEGER_CONSTANT_1 : Integer := 15;\n";
		structure += "static attribute staticBooleanAttribute : Boolean;\n";
		structure += "static operation staticGetANumber() : Integer;\n";
		structure += "static operation staticGetADoubleNumber() : Double;\n";
		structure += "static operation staticGetABoolean() : Boolean;\n";
		structure += "static operation staticOperateOnInteger(value :  Integer) : Integer;\n";
		structure += "static operation staticOperateOnTwoIntegers(value1 :  Integer, value2 : Integer) : Integer;\n";
		structure += "static operation staticOperateOnTwoDoubles(value1 :  Double, value2 : Double) : Double;\n";
		structure += "static operation staticOperateOnBoolean(value :  Boolean) : Boolean;\n";
		structure += "static operation staticOperateOnTwoBooleans(value1 :  Boolean, value2 : Boolean) : Boolean;\n";
		structure += "static operation staticDoubleNumber(number : Integer) : Integer;\n";
		structure += "static operation staticDoubleDouble(number : Double) : Double;\n";
		structure += "static operation staticFatorial(number : Integer) : Integer;\n";
		structure += "end;\n";
		structure += "end.";
	}

	public static Test suite() {
		return new TestSuite(PrimitiveActionTests.class);
	}

	public PrimitiveActionTests(String arg0) {
		super(arg0);
	}

	public void testAssignment() throws CoreException {
		String behavior = "";
		behavior += "model tests;\n";
		behavior += "operation Simple.staticGetANumber;\n";
		behavior += "begin\n";
		behavior += "var value : Integer;\n";
		behavior += "value := 10;";
		behavior += "return value;\n";
		behavior += "end;\n";
		behavior += "end.";
		parseAndCheck(structure, behavior);
		assertEquals(new IntegerType(10), runStaticOperation("tests::Simple", "staticGetANumber"));
	}

	public void testCallStaticOperation() throws CoreException {
		String behavior = "";
		behavior += "model tests;\n";
		behavior += "operation Simple.staticGetANumber;\n";
		behavior += "begin\n";
		behavior += "return Simple#staticDoubleNumber(5);\n";
		behavior += "end;\n";
		behavior += "operation Simple.staticDoubleNumber;\n";
		behavior += "begin\n";
		behavior += "return number * 2;\n";
		behavior += "end;\n";
		behavior += "operation Simple.staticDoubleDouble;\n";
		behavior += "begin\n";
		behavior += "return number * 2;\n";
		behavior += "end;\n";
		behavior += "end.";
		String[] sources = { structure, behavior };
		parseAndCheck(sources);
		assertEquals(new IntegerType(10), runStaticOperation("tests::Simple", "staticGetANumber"));
		assertEquals(new RealType(11), runStaticOperation("tests::Simple", "staticDoubleDouble", new RealType(5.5)));
	}

	public void testConstant() throws CoreException {
		String behavior = "";
		behavior += "model tests;\n";
		behavior += "operation Simple.staticGetANumber;\n";
		behavior += "begin\n";
		behavior += "return 2 * Simple#INTEGER_CONSTANT_1;\n";
		behavior += "end;\n";
		behavior += "end.";
		String[] sources = { structure, behavior };
		parseAndCheck(sources);
		Object result = runStaticOperation("tests::Simple", "staticGetANumber");
		assertEquals(new IntegerType(15 * 2), result);
	}

	public void testIf() throws CoreException {
		String behavior = "";
		behavior += "model tests;\n";
		behavior += "operation Simple.staticOperateOnTwoIntegers;\n";
		behavior += "begin\n";
		behavior += "var result : Integer;\n";
		behavior += "if (value1 < value2) then\n";
		behavior += "result := -1\n";
		behavior += "elseif (value1 > value2) then\n";
		behavior += "result := 1\n";
		behavior += "else\n";
		behavior += "result := 0;\n";
		behavior += "return result;\n";
		behavior += "end;\n";
		behavior += "end.";
		String[] sources = { structure, behavior };
		parseAndCheck(sources);
		assertEquals(new IntegerType(1), runStaticOperation("tests::Simple", "staticOperateOnTwoIntegers", new IntegerType(10), new IntegerType(-5)));
		assertEquals(new IntegerType(-1), runStaticOperation("tests::Simple", "staticOperateOnTwoIntegers", new IntegerType(1), new IntegerType(1000)));
		assertEquals(new IntegerType(0), runStaticOperation("tests::Simple", "staticOperateOnTwoIntegers", new IntegerType(555), new IntegerType(555)));
	}

	public void testLiteralValue() throws CoreException {
		String behavior = "";
		behavior += "model tests;\n";
		behavior += "operation Simple.staticGetANumber;\n";
		behavior += "begin\n";
		behavior += "return 10;\n";
		behavior += "end;\n";
		behavior += "end.";
		String[] sources = { structure, behavior };
		parseAndCheck(sources);
		assertEquals(new IntegerType(10), runStaticOperation("tests::Simple", "staticGetANumber"));
	}
	
	public void testLiteralDoubleValue() throws CoreException {
		String behavior = "";
		behavior += "model tests;\n";
		behavior += "operation Simple.staticGetADoubleNumber;\n";
		behavior += "begin\n";
		behavior += "return 10.5;\n";
		behavior += "end;\n";
		behavior += "end.";
		String[] sources = { structure, behavior };
		parseAndCheck(sources);
		assertEquals(new RealType(10.5), runStaticOperation("tests::Simple", "staticGetADoubleNumber"));
	}

	public void testReadWriteAttribute() throws CoreException {
		String behavior = "";
		behavior += "model tests;\n";
		behavior += "operation Simple.operateOnInteger;\n";
		behavior += "begin\n";
		behavior += "self.integerAttribute1 := value;\n";
		behavior += "return self.integerAttribute1;\n";
		behavior += "end;\n";
		behavior += "operation Simple.getANumber;\n";
		behavior += "begin\n";
		behavior += "return self.integerAttribute1;\n";
		behavior += "end;\n";
		behavior += "end.";
		String[] sources = { structure, behavior };
		parseAndCheck(sources);
		RuntimeObject targetObject = newInstance("tests::Simple");
		runOperation(targetObject, "operateOnInteger", new IntegerType(50));
		Object result = runOperation(targetObject, "getANumber");
		assertEquals(new IntegerType(50), result);
	}

	public void testReadDerivedAttribute() throws CoreException {
		String behavior = "";
		behavior += "model tests;\n";
		behavior += "operation Simple.operateOnInteger;\n";
		behavior += "begin\n";
		behavior += "self.integerAttribute1 := value;\n";
		behavior += "return self.integerAttribute1;\n";
		behavior += "end;\n";
		behavior += "operation Simple.getANumber;\n";
		behavior += "begin\n";
		behavior += "return self.doubleIntegerAttribute1;\n";
		behavior += "end;\n";
		behavior += "operation Simple.getABoolean;\n";
		behavior += "begin\n";
		behavior += "return self.integerAttribute1 > 0;\n";
		behavior += "end;\n";
		behavior += "end.";
		String[] sources = { structure, behavior };
		parseAndCheck(sources);
		RuntimeObject targetObject = newInstance("tests::Simple");

		runOperation(targetObject, "operateOnInteger", new IntegerType(50));
		assertEquals(new IntegerType(100), runOperation(targetObject, "getANumber"));
		assertEquals(BooleanType.TRUE, runOperation(targetObject, "getABoolean"));
		
		runOperation(targetObject, "operateOnInteger", new IntegerType(-50));
		assertEquals(new IntegerType(-100), runOperation(targetObject, "getANumber"));
		assertEquals(BooleanType.FALSE, runOperation(targetObject, "getABoolean"));
		
		runOperation(targetObject, "operateOnInteger", new IntegerType(0));
		assertEquals(new IntegerType(0), runOperation(targetObject, "getANumber"));
		assertEquals(BooleanType.FALSE, runOperation(targetObject, "getABoolean"));
	}

	public void testWhile() throws CoreException {
		String behavior = "";
		behavior += "model tests;\n";
		behavior += "operation Simple.staticFatorial;\n";
		behavior += "begin\n";
		behavior += "  var result : Integer;\n";
		behavior += "  result := 1;\n";
		behavior += "  while (number > 1) do\n";
		behavior += "  begin\n";
		behavior += "    result := result * number;\n";
		behavior += "    number := number - 1;\n";
		behavior += "  end;\n";
		behavior += "  return result;\n";
		behavior += "end;\n";
		behavior += "end.";
		String[] sources = { structure, behavior };
		parseAndCheck(sources);
		assertEquals(new IntegerType(1), runStaticOperation("tests::Simple", "staticFatorial", new IntegerType(0)));
		assertEquals(new IntegerType(1), runStaticOperation("tests::Simple", "staticFatorial", new IntegerType(1)));
		assertEquals(new IntegerType(2), runStaticOperation("tests::Simple", "staticFatorial", new IntegerType(2)));
		assertEquals(new IntegerType(6), runStaticOperation("tests::Simple", "staticFatorial", new IntegerType(3)));
		assertEquals(new IntegerType(24), runStaticOperation("tests::Simple", "staticFatorial", new IntegerType(4)));
		assertEquals(new IntegerType(120), runStaticOperation("tests::Simple", "staticFatorial", new IntegerType(5)));
	}
}