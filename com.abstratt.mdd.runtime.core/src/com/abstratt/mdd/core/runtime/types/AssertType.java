package com.abstratt.mdd.core.runtime.types;

import com.abstratt.mdd.core.runtime.ExecutionContext;
import com.abstratt.mdd.core.runtime.RuntimeObject;
import com.abstratt.mdd.core.runtime.RuntimeRaisedException;

public class AssertType extends BuiltInClass {
    private AssertType() {
    }
    
    @Override
    public String getClassifierName() {
        return "mdd_types::System";
    }
    public static RuntimeObject user(ExecutionContext context) {
        return context.getRuntime().getCurrentActor();
    }
	public static RuntimeObject areEqual(ExecutionContext context, BasicType expected, BasicType actual) {
		if (expected == null || !expected.equals(context, actual).isTrue())
	        throw new RuntimeRaisedException(new StringType((expected == null ? null : expected.toString()) + " != " + (actual == null ? null : actual.toString())), null, null);
		return null;
    }
	public static RuntimeObject isTrue(ExecutionContext context, BasicType actual) {
		if (actual == null)
	        throw new RuntimeRaisedException(new StringType("Value is null"), null, null);
		return null;
    }
}