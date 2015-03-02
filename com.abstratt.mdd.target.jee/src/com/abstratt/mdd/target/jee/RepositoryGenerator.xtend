package com.abstratt.mdd.target.jee

import com.abstratt.mdd.core.IRepository
import org.eclipse.uml2.uml.Class
import org.eclipse.uml2.uml.Classifier

class RepositoryGenerator extends EntityGenerator {
    
    new(IRepository repository) {
        super(repository)
    }
    
    def generateRepository(Class entity) {
        '''
        package «entity.packagePrefix»;

        «generateStandardImports»        
        
        «entity.generateImports»

        @Stateless
        public class «entity.name»Repository {
            @Inject
            EntityManager entityManager;
            
            «entity.generateCreate»
            «entity.generateFind»
        }
        '''
    }
    
    def generateCreate(Classifier entity) {
        '''
        public «entity.name» create(«entity.name» toCreate) {
            entityManager.persist(toCreate);
            return toCreate;
        }
        '''
    }
    
    def generateFind(Classifier entity) {
        '''
        public «entity.name» find(Serializable id) {
            return entityManager.find(«entity.name».class, id);
        }
        '''
    }
}