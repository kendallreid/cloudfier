package com.abstratt.mdd.core.runtime.types;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.abstratt.mdd.core.runtime.ExecutionContext;

public class BooleanType extends PrimitiveType<Boolean> {
	public final static BooleanType FALSE = new BooleanType(false);

	private static final long serialVersionUID = 1L;

	public final static BooleanType TRUE = new BooleanType(true);

	private boolean value;

	public static BooleanType fromString(java.lang.String stringValue) {
		return new BooleanType(java.lang.Boolean.TRUE.toString().equalsIgnoreCase(stringValue));
	}

	public static BooleanType fromValue(boolean value) {
		return value ? TRUE : FALSE;
	}

	private BooleanType(boolean value) {
		this.value = value;
	}

	public BooleanType and(ExecutionContext context, BooleanType anotherBoolean) {
		return fromValue(this.value && anotherBoolean.value);
	}

	@Override
	public String getClassifierName() {
		return "mdd_types::Boolean";
	}

	public boolean isTrue() {
		return value;
	}

	public BooleanType not(ExecutionContext context) {
		return fromValue(!value);
	}

	public BooleanType or(ExecutionContext context, BooleanType anotherBoolean) {
		return fromValue(this.value || anotherBoolean.value);
	}

	public Boolean primitiveValue() {
		return value;
	}

	public void readFrom(DataInput in) throws IOException {
		this.value = in.readBoolean();
	}
	
	public void writeTo(DataOutput out) throws IOException {
		out.writeBoolean(value);
	}
}