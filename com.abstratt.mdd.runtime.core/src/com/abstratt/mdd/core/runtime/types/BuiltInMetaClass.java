package com.abstratt.mdd.core.runtime.types;

import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Signal;

import com.abstratt.mdd.core.runtime.ExecutionContext;
import com.abstratt.mdd.core.runtime.MetaClass;
import com.abstratt.mdd.core.runtime.RuntimeEvent;

public enum BuiltInMetaClass implements MetaClass<BuiltInClass> {
	System("mdd_types::System", SystemType.class),
	Assert("mdd_types::Assert", AssertType.class),
	Boolean("mdd_types::Boolean", BooleanType.class), 
    Integer("mdd_types::Integer", IntegerType.class), 
    Decimal("mdd_types::Double", RealType.class),
    String("mdd_types::String", StringType.class),
    Memo("mdd_types::Memo", MemoType.class),
    Date("mdd_types::Date", DateType.class),
    Set("mdd_collections::Set", SetType.class), 
    Bag("mdd_collections::Bag", BagType.class), 
    OrderedSet("mdd_collections::OrderedSet", OrderedSetType.class),
    Sequence("mdd_collections::Sequence", SequenceType.class),
    Grouping("mdd_collections::Grouping", GroupingType.class);
    BuiltInMetaClass(String classifierName, Class<? extends BuiltInClass> javaClass) {
        this.classifierName = classifierName;
        this.javaClass = javaClass;
    }
    private String classifierName;
    private Class<? extends BuiltInClass> javaClass;
    public static boolean isBuiltIn(String classifierName) {
        for (BuiltInMetaClass builtInType : values()) {
            if (builtInType.classifierName.equals(classifierName))
                return true;
        }
        return false;
    }
    public static BuiltInMetaClass  findBuiltInClass(String classifierName) {
        for (BuiltInMetaClass builtInType : values())
            if (builtInType.classifierName.equals(classifierName))
                return builtInType;
        throw new IllegalArgumentException(classifierName + " is not a built-in type");
    }
    @Override
    public Object runOperation(ExecutionContext context, BasicType target,
            Operation operation, Object... arguments) {
        return BasicType.runNativeOperation(context, javaClass, target, operation, arguments);
    }
    @Override
    public void handleEvent(RuntimeEvent runtimeEvent) {
    }
}