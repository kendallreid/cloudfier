package com.abstratt.mdd.core.runtime;

import org.eclipse.uml2.uml.NamedElement;

public class ModelExecutionException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private RuntimeAction executing;
	private NamedElement context;

	public ModelExecutionException(String message, NamedElement context, RuntimeAction executing) {
		super(message);
		this.executing = executing;
		this.context = context;
	}

	public RuntimeAction getExecuting() {
		return executing;
	}

	public void setExecuting(RuntimeAction executing) {
		this.executing = executing;
	}
	
    public NamedElement getContext() {
		return context;
	}
    
    @Override
    public String getMessage() {
    	return super.getMessage();
    }

	protected String getContextName() {
		return context == null ? null : context.getQualifiedName();
	}
}
