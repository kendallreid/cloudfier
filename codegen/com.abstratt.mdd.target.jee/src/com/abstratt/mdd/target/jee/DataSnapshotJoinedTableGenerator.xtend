package com.abstratt.mdd.target.jee

import com.abstratt.kirra.InstanceRef
import com.abstratt.mdd.core.IRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import java.util.Map
import java.util.Set
import java.util.concurrent.atomic.AtomicLong
import org.eclipse.uml2.uml.Class
import org.eclipse.uml2.uml.Property

import static extension com.abstratt.kirra.mdd.core.KirraHelper.*

class DataSnapshotJoinedTableGenerator extends DataSnapshotGenerator {
	
	new(IRepository repository) {
		super(repository)
	}
	
	override def getIdAnchorEntity(Class entity) {
		val hierarchy = findHierarchy(entity)
	    val rootEntity = hierarchy.last
	    return rootEntity
	}

	/**
	 * In this strategy, a single instance is mapped to potentially many database rows, one
	 * per table representing each class (concrete or abstract) in the hierarchy.
	 * 
	 * Note that when an instance is broken up into multiple rows, each row will have the same
	 * PK. The id is the one defined for the base class.
	 */
	def override Iterable<CharSequence> generateInstance(Class entity, String namespace, String className, long index,
		ObjectNode node) {
		val id = generateId(entity, index)
		println('''«entity.qualifiedName» =  «id»''')
		
		val hierarchy = findHierarchy(entity)

		val propertiesPerEntity = hierarchy.map[qualifiedName].toInvertedMap[newArrayList]
		val allEntityProperties = entity.properties.filter[!autoGenerated]
		allEntityProperties.forEach [
			val collection = propertiesPerEntity.get(it.class_.qualifiedName)
			collection.add(it)
		]

		val relationshipsPerEntity = hierarchy.map[qualifiedName].toInvertedMap[newArrayList]
		val allEntityRelationships = entity.entityRelationships.filter[!derived && primary && !multivalued]
		allEntityRelationships.forEach [
			relationshipsPerEntity.get(it.class_.qualifiedName).add(it)
		]

		return hierarchy.toList.reverseView.map [ current |
			val entityProperties = propertiesPerEntity.get(current.qualifiedName)
			val entityRelationships = relationshipsPerEntity.get(current.qualifiedName)
			generateInsert(entityProperties, entityRelationships, node, current.name, id)
		]
	}
	
	def generateId(Class entity, long index) {
		return idMapping.get(InstanceRef.toString(entity.package.name, entity.name, "" + index))
	}
	
	override def CharSequence getRelatedInstanceId(Property relationship, JsonNode propertyValue) {
		val originalReferenceString = (propertyValue as TextNode).textValue
		println("idMapping=" + idMapping)
		println("referenceString=" + originalReferenceString)
		
		// if the type is not qualified, do it
		val ref = InstanceRef.parse(originalReferenceString, relationship.nearestPackage.name) 
		val referenceString = ref.toString()
		return "" + idMapping.get(referenceString)
	}	
	
	override def Iterable<String> generateAlterSequences(Map<String, AtomicLong> ids, String namespace, Map<String, Class> entities) {
		val generated = ids.entrySet.filter[!entities.get(it.key).superClasses.exists[it.entity]].map [ pair |
			val entity = entities.get(pair.key)
			val nextValue = pair.value.get + 1
			'''«generateAlterSequenceStatement(applicationName, entity, nextValue)»'''
		]
		return generated
	}
	def private Set<Class> collectAllSuperClasses(Class current, Set<Class> collected) {
		val superClasses = current.superClasses
		collected.addAll(superClasses)
		superClasses.forEach[collectAllSuperClasses(it, collected)]
		return collected
	}

}
