package com.abstratt.mdd.target.jee

import com.abstratt.kirra.NamedElement
import com.abstratt.mdd.core.IRepository
import com.abstratt.mdd.core.util.MDDUtil
import com.abstratt.mdd.target.jse.AbstractGenerator
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.Reader
import java.io.StringWriter
import java.util.Map
import java.util.concurrent.atomic.AtomicLong
import org.apache.commons.io.IOUtils
import org.eclipse.uml2.uml.Activity
import org.eclipse.uml2.uml.Class
import org.eclipse.uml2.uml.Element
import org.eclipse.uml2.uml.Enumeration
import org.eclipse.uml2.uml.EnumerationLiteral
import org.eclipse.uml2.uml.InstanceValue
import org.eclipse.uml2.uml.LiteralBoolean
import org.eclipse.uml2.uml.LiteralNull
import org.eclipse.uml2.uml.LiteralString
import org.eclipse.uml2.uml.OpaqueExpression
import org.eclipse.uml2.uml.Property
import org.eclipse.uml2.uml.StateMachine
import org.eclipse.uml2.uml.Type
import org.eclipse.uml2.uml.ValueSpecification

import static extension com.abstratt.kirra.mdd.core.KirraHelper.*
import static extension com.abstratt.mdd.core.util.ActivityUtils.*
import static extension com.abstratt.mdd.core.util.MDDExtensionUtils.*
import static extension com.abstratt.mdd.core.util.StateMachineUtils.*

class DataSnapshotGenerator extends AbstractGenerator {
    
    new(IRepository repository) {
        super(repository)
        if (appPackages != null) {
        	// contrary to elsewhere, we do care about non-top-level entities here
            this.entities = appPackages.entities
        }
    }
    
    def CharSequence generate() {
        val dataUrl = MDDUtil.fromEMFToJava(repository.baseURI.appendSegment("data.json")).toURL
        val contents = new ByteArrayOutputStream()
        var InputStream sourceStream
        try {
            sourceStream = dataUrl.openStream
            IOUtils.copy(sourceStream, contents)
        } catch (IOException e) {
            val stringWriter = new StringWriter()
            e.printStackTrace(new PrintWriter(stringWriter))
            return '-- ERROR\n'+ stringWriter.buffer.toString.split('\n').map['''-- «it»'''].join('\n')  
        } finally {
            IOUtils.closeQuietly(sourceStream)
        }
        val jsonContents = new String(contents.toByteArray)
        System.out.println(jsonContents)
        val jsonTree = parse(new InputStreamReader(new ByteArrayInputStream(contents.toByteArray)))
        generateContents(jsonTree as ObjectNode)
    }
    
    def CharSequence generateContents(ObjectNode root) {
        val entities = entities.toMap[qualifiedName]
        val entityNames = entities.keySet() 
		val ids = entityNames.toInvertedMap[ new AtomicLong(0) ]
        val namespaceStatements = root.fields.toIterable.map[ entry |
            val namespaceName = entry.key
            val namespaceNode = entry.value as ObjectNode
            generateNamespace(entities, ids, namespaceName, namespaceNode)
        ]
        return namespaceStatements.flatten.join('\n') 
    }
    
    def Iterable<CharSequence> generateNamespace(Map<String, Class> entities, Map<String, AtomicLong> ids, String namespace, ObjectNode namespaceContents) {
        val inserts = namespaceContents.fields.toIterable.map[ entry |
            val className = entry.key
            val instanceNodes = (entry.value as ArrayNode)
            val entity = entities.get(namespace + '::' + className)
            val id = ids.get(namespace + '::' + className)
            instanceNodes.elements.map[generateInstance(entity, namespace, className, it as ObjectNode, id.incrementAndGet)].join()
        ]
        val alterSequences = ids.entrySet.map[ pair |
            '''
            ALTER SEQUENCE «namespace».«entities.get(pair.key).name.toLowerCase»_id_seq RESTART WITH «pair.value.get + 1»;
            '''
        ]
        return (inserts + alterSequences)
    }
    
    def CharSequence generateInstance(Class entity, String namespace, String className, ObjectNode node, Long id) {
        val sqlPropertyValues = entity.properties.filter[!derived].toMap [ name ].mapValues[ property |
            val jsonValue = node.get(property.name)
            toSqlValue(property, jsonValue)
        ]
        
        val sqlForeignKeys = entity.entityRelationships
        	.filter[!derived && primary && !multivalued]
        	.toMap [ name ]
        	.filter[ relationshipName, value |
        		node.get(relationshipName) != null
        	]
        	.mapValues[ relationship |
            	getRelatedInstanceId(node.get(relationship.name))
        	]
        
        val sqlValues = newHashMap()
        sqlPropertyValues.forEach[key, value | sqlValues.put(key, value)]
        sqlForeignKeys.forEach[key, value | sqlValues.put(key + '_id', value)]
        
        '''
        INSERT INTO «namespace».«className» (id, «sqlValues.keySet.join(', ')») VALUES («id», «sqlValues.keySet.map[sqlValues.get(it)].join(', ')»);
        '''
    }
    
    def CharSequence getRelatedInstanceId(JsonNode propertyValue) {
        val referenceString = (propertyValue as TextNode).textValue
        val addressSeparatorIndex = referenceString.indexOf('@')
        if (addressSeparatorIndex <= 0 || addressSeparatorIndex == referenceString.length() - 1)
            return null
        return referenceString.substring(addressSeparatorIndex + 1)
    }
    
    def CharSequence toSqlValue(Property property, JsonNode propertyValue) {
        if (propertyValue == null)
            return property.generateDefaultSqlValue
        return switch (propertyValue.asToken()) {
            case VALUE_NULL:
                'null'
            case VALUE_TRUE:
                'true'
            case VALUE_FALSE:
                'false'
            case VALUE_STRING:
                ''' '«(propertyValue as TextNode).textValue»' '''.toString.trim()
            case VALUE_NUMBER_INT:
                '''«propertyValue.asText()»'''
            case VALUE_NUMBER_FLOAT:
                '''«propertyValue.asText()»'''
            }
    }
	
	def CharSequence generateDefaultSqlValue(Property attribute) {
        if (attribute.defaultValue != null) {
            if (attribute.defaultValue.behaviorReference)
                (attribute.defaultValue.resolveBehaviorReference as Activity).generateActivityAsExpression 
            else
                attribute.defaultValue.generateValue
        } else if (attribute.required || attribute.type.enumeration)
            // enumeration covers state machines as well
            attribute.type.generateDefaultValue
	}
	
	def CharSequence generateActivityAsExpression(Activity activity) {
		//TODO
		'null'
	}
	
    def CharSequence unsupported(CharSequence message) {
        '''Unsupported: «message»''' 
    } 
    
    def CharSequence unsupportedElement(Element e, String message) {
 	   unsupported('''«e.eClass.name»> «if (message != null) '''(«message»)''' else ''»''')
    }
    
    def CharSequence unsupportedElement(Element e) {
        unsupportedElement(e, if (e instanceof NamedElement) e.name else null)
    }    
	
	def CharSequence generateValue(ValueSpecification value) {
	        switch (value) {
            // the TextUML compiler maps all primitive values to LiteralString
            LiteralString : switch (value.type.name) {
                case 'String' : '''«'\''»«value.stringValue»«'\''»'''
                case 'Integer' : '''«value.stringValue»'''
                case 'Double' : '''«value.stringValue»'''
                case 'Boolean' : '''«value.stringValue»'''
                default : unsupported(value.stringValue)
            }
            LiteralBoolean : '''«value.booleanValue»'''
            LiteralNull : switch (value) {
                case value.isVertexLiteral : '''«'\''»«value.resolveVertexLiteral.name»«'\''»'''
                default : 'null'
            }
            OpaqueExpression case value.behaviorReference : (value.resolveBehaviorReference as Activity).generateActivityAsExpression()
            InstanceValue case value.instance instanceof EnumerationLiteral: '''«value.instance.namespace.name».«value.instance.name»'''
            default : unsupportedElement(value)
        }
	}
	
	def CharSequence generateDefaultValue(Type type) {
        switch (type) {
            StateMachine : '''«'\''»«type.initialVertex.name»«'\''»'''
            Enumeration : '''«type.name».«type.ownedLiterals.head.name»'''
            Class : switch (type.name) {
                case 'Boolean' : 'false'
                case 'Integer' : '0'
                case 'Double' : '0'
                case 'Date' : 'now()'
                case 'String' : '\'\''
                case 'Memo' : '\'\''
            }
            default : null
        }
	}
    
    private def static JsonNode parse(Reader contents) throws IOException, JsonParseException, JsonProcessingException {
        val parser = jsonFactory.createJsonParser(contents);
        parser.setCodec(new ObjectMapper());
        return parser.readValueAsTree();
    }

    private static JsonFactory jsonFactory = [|
        val factory = new JsonFactory();
        factory.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        factory.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        factory.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        factory.configure(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES, false);
        return factory;
    ].apply
}