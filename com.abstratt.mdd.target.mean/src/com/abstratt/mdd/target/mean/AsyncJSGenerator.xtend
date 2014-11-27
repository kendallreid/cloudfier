package com.abstratt.mdd.target.mean

import com.abstratt.mdd.core.util.ActivityUtils
import com.abstratt.mdd.target.mean.ActivityContext.Stage
import org.eclipse.uml2.uml.Action
import org.eclipse.uml2.uml.Activity
import org.eclipse.uml2.uml.Operation
import org.eclipse.uml2.uml.StructuredActivityNode

import static extension com.abstratt.mdd.core.util.ActivityUtils.*
import static extension com.abstratt.kirra.mdd.core.KirraHelper.*
import org.eclipse.uml2.uml.ReadSelfAction
import org.eclipse.uml2.uml.SendSignalAction
import org.eclipse.uml2.uml.CallOperationAction

class AsyncJSGenerator extends JSGenerator {
    
    final protected ApplicationContext application = new ApplicationContext


    override generateActivityRootAction(Activity activity) {
        application.newActivityContext(activity)
        try {
            if (!application.isAsynchronous(activity)) {
                return super.generateActivityRootAction(activity)
            }
            application.activityContext.buildPipeline(activity.rootAction)
            '''«generatePipeline()»'''
        } finally {
            application.dropActivityContext
        }
    }
    
    def ActivityContext getContext() {
        application.activityContext
    }
    
    def CharSequence generateStage(Stage stage, boolean expression) {
        val kernel = switch (stage.substages.size) {
            case 0 : generateLeafStage(stage)
            case 1 : generateStageSingleChild(stage)
            default : generateStageMultipleChildren(stage)
        }
        val optionalReturn = if (expression) '' else 'return '
        val optionalSemicolon = if (expression || kernel.toString.trim.endsWith(';')) '' else ';'
        '''
        «optionalReturn»«kernel»«generateStageSuffix(stage)»«optionalSemicolon»
        '''
    }
    
    def CharSequence generateStageSuffix(Stage stage) {
        ''
    }
    
    
    def generateReturn(Action rootAction) {
        val kernel = rootAction.generateAction
        if (rootAction.outputs.empty && !application.isAsynchronous(rootAction)) {
            val optionalSemicolon = if (kernel.toString.trim.endsWith(';')) '' else ';'
            return '''
            «kernel»«optionalSemicolon»
            '''
        }
        '''return «kernel»;'''
    }
    
    def dump(CharSequence generated) {
        var asString = generated.toString
        '''console.log("«asString.replaceAll('\\n', '\\\\n').replaceAll('"', '\\\\"')»");'''
    }
    
    def generateLeafStage(Stage stage) {
        val kernel = stage.rootAction.generateReturn
        '''
        Q().then(function() {
            «dump(kernel)»
            «kernel»
        })'''
    }
    
    def generateStageMultipleChildren(Stage stage) {
        val rootAction = stage.rootAction
        if (rootAction instanceof StructuredActivityNode)
            // ignore mustIsolate (which TextUML should be generating but may not)
            return generateStageMultipleChildrenSequential(stage)
        return generateStageMultipleChildrenParallel(stage)
    }
    
    def generateStageMultipleChildrenSequential(Stage stage) {
        '''Q()«stage.substages.map[generateStage(false)].map[
        '''
        .then(function() {
            «it»
        })'''.toString.trim].join('')»''' 
    }
    
    def generateStageMultipleChildrenParallel(Stage stage) {
        '''
        Q.all([
            «stage.substages.map[generateStage(true).toString.trim].join(',\n')»
        ]).spread(function(«stage.substages.map[alias].join(', ')») {
            «stage.rootAction.generateReturn»
        })'''
    }
    
    def generateStageSingleChild(Stage stage) {
        val singleChild = stage.substages.head
        val isBlock = stage.rootAction instanceof StructuredActivityNode
        val childKernel = singleChild.generateStage(true)
        val thisKernel = stage.rootAction.generateReturn()
        '''
        «childKernel.toString.trim()»«IF !isBlock».then(function(«singleChild.alias») {
            console.log(«singleChild.alias»);
            «dump(thisKernel)»
            «thisKernel»
        })«ENDIF»'''
    }
    
    def generatePipelineFrom(Stage rootStage) {
        val rootStageVariables = context.findVariables
        // always generate a return, caller may be interested in promise
        '''
        «IF !rootStageVariables.empty»
        «generateVariableBlock(rootStageVariables)»
        «ENDIF» 
        var me = this;
        «context.rootStage.generateStage(false)»
        '''
    }
    
    override generateVariables(StructuredActivityNode node) {
        // do not declare variables in async blocks/actions, as those need to be in the 
        // shared closure for all blocks
        if (application.isAsynchronous(node.actionActivity)) '' else super.generateVariables(node)
    }

    def generatePipeline() {
        if (context.activity != null && !application.isAsynchronous(context.activity))
            throw new IllegalStateException
        if (context.stagedActions.empty)
            return ''
        if (context.rootStage.substages.size == 1)
            return generatePipelineFrom(context.rootStage.substages.get(0))
        generatePipelineFrom(context.rootStage)        
    }

    def void addActionPrologue(Operation action) {
    }

    def void addActionEpilogue(Operation action) {
    }
    
    override generateSelfReference() {
        if (context == null || !this.application.isAsynchronous(context.activity))
            super.generateSelfReference
        else
            'me'
    }
        
    override generateSendSignalAction(SendSignalAction action) {
        if (!this.application.isAsynchronous(action.actionActivity))
            super.generateSendSignalAction(action)
        else
            '''
            «super.generateSendSignalAction(action)»
            '''
    }

    override generateActionProper(Action toGenerate) {
        val stage = context.findStage(toGenerate)
        if (application.isAsynchronous(context.activity)) {
            if (stage.rootAction == toGenerate) {
                if (stage.generated) {
                    val previouslyGeneratedStage = context.findStage(toGenerate)
                    return if (previouslyGeneratedStage.producer) previouslyGeneratedStage.alias else '/*sink*/'
                }
                stage.markGenerated
            }
        }
        '''«super.generateActionProper(toGenerate)»'''
    }
}
