package com.abstratt.mdd.target.tests.jee

import com.abstratt.mdd.core.tests.harness.AssertHelper
import com.abstratt.mdd.target.jee.QueryActionGenerator
import com.abstratt.mdd.target.tests.AbstractGeneratorTest
import java.io.IOException
import org.eclipse.core.runtime.CoreException
import org.eclipse.uml2.uml.Operation

import static extension com.abstratt.mdd.core.util.ActivityUtils.*

class QueryActionGeneratorTests extends AbstractGeneratorTest {
    new(String name) {
        super(name)
    }

    def void testExtent() throws CoreException, IOException {
        var source = '''
            model crm;
            class Customer
                attribute name : String;  
                query findAll() : Customer[*];
                begin
                    return Customer extent;
                end;
            end;
            end.
        '''
        parseAndCheck(source)
        val op = getOperation('crm::Customer::findAll')

        val root = getStatementSourceAction(op)
        val generated = new QueryActionGenerator(repository).generateAction(root)
        AssertHelper.assertStringsEqual(
            '''
                cq.select(customer_).distinct(true)
            ''', generated.toString)
    }

    def void testSelectByBooleanValue() throws CoreException, IOException {
        var source = '''
            model crm;
            class Customer
                attribute name : String;
                attribute vip : Boolean;              
                query findVip() : Customer[*];
                begin
                    return Customer extent.select((c : Customer) : Boolean {
                        c.vip
                    });
                end;
            end;
            end.
        '''
        parseAndCheck(source)
        val op = getOperation('crm::Customer::findVip')
        val root = getStatementSourceAction(op)
        val generated = new QueryActionGenerator(repository).generateAction(root)
        AssertHelper.assertStringsEqual(
            '''
                cq.select(customer_)
                    .distinct(true)
                    .where(cb.isTrue(customer_.get("vip")))
            ''', generated.toString)
    }

    def void testSelectByAttributeInRelatedEntity() throws CoreException, IOException {
        var source = '''
            model crm;
            class Company
                attribute revenue : Double;
            end;
            class Customer
                attribute name : String;
                attribute company : Company;              
                query findByCompanyRevenue(threshold : Double) : Customer[*];
                begin
                    return Customer extent.select((c : Customer) : Boolean {
                        c.company.revenue >= threshold
                    });
                end;
            end;
            end.
        '''
        parseAndCheck(source)
        val op = getOperation('crm::Customer::findByCompanyRevenue')
        val root = getStatementSourceAction(op)
        val generated = new QueryActionGenerator(repository).generateAction(root)
        AssertHelper.assertStringsEqual(
            '''
                cq.select(customer_)
                    .distinct(true)
                    .where(cb.greaterThanOrEqualTo(
                        company_.get("revenue"),
                        cb.parameter(Double.class,"threshold")
                    ))
            ''', generated.toString)
    }


    def void testSelectByRelatedEntity() throws CoreException, IOException {
        var source = '''
            model crm;
            class Company
                attribute revenue : Double;
            end;
            class Customer
                attribute name : String;
                attribute company : Company;              
                query findByCompany(toMatch : Company) : Customer[*];
                begin
                    return Customer extent.select((c : Customer) : Boolean {
                        c.company == toMatch
                    });
                end;
            end;
            end.
        '''
        parseAndCheck(source)
        val op = getOperation('crm::Customer::findByCompany')
        val root = getStatementSourceAction(op)
        val generated = new QueryActionGenerator(repository).generateAction(root)
        AssertHelper.assertStringsEqual(
            '''
                cq.select(customer_)
                    .distinct(true)
                    .where(cb.equal(
                        customer_.get("company"),
                        cb.parameter(Company.class,"toMatch")
                    ))
            ''', generated.toString)
    }


    def void testSelectByDoubleComparison() throws CoreException, IOException {
        var source = '''
            model crm;
            class Customer
                attribute name : String;
                attribute vip : Boolean;
                attribute salary : Double;
                query findHighestGrossing(threshold : Double) : Customer[*];
                begin
                    return Customer extent.select((c : Customer) : Boolean {
                        c.salary >= threshold
                    });
                end;
            end;
            end.
        '''
        parseAndCheck(source)
        val op = getOperation('crm::Customer::findHighestGrossing')
        val root = getStatementSourceAction(op)
        val generated = new QueryActionGenerator(repository).generateAction(root)
        AssertHelper.assertStringsEqual(
            '''
                cq.select(customer_)
                    .distinct(true)
                    .where(cb.greaterThanOrEqualTo(
                        customer_.get("salary"),
                        cb.parameter(Double.class,"threshold")
                    ))
            ''', generated.toString)
    }

    def void testGroupByAttributeIntoCountWithFilter() throws CoreException, IOException {
        var source = '''
            model crm;
            class Customer
                attribute name : String;
                attribute title : String;              
                query countByTitle() : {title : String, customerCount : Integer} [*];
                begin
                    return Customer extent.groupBy((c : Customer) : String {
                        c.title
                    }).groupCollect((group : Customer[*]) : {title:String, customerCount : Integer} {
                        { 
                            title := group.one().title,
                            customerCount := group.size()
                        }   
                    }).select((counted : {title:String, customerCount : Integer}) : Boolean {
                        counted.customerCount > 100
                    });
                end;
            end;
            end.
        '''
        parseAndCheck(source)
        val op = getOperation('crm::Customer::countByTitle')
        val root = getStatementSourceAction(op)
        val generated = new QueryActionGenerator(repository).generateAction(root)
        AssertHelper.assertStringsEqual(
            '''
                cq
                    .groupBy(customer_.get("title"))
                    .multiselect(customer_.get("title"), cb.count(customer_))
                    .having(cb.greaterThan(cb.count(customer_), cb.literal(100)))
            ''', generated.toString)
    }
    
    def void testGroupByAttributeIntoCount() throws CoreException, IOException {
        var source = '''
            model crm;
            class Customer
                attribute name : String;
                attribute title : String;              
                query countByTitle() : {title : String, customerCount : Integer} [*];
                begin
                    return Customer extent.groupBy((c : Customer) : String {
                        c.title
                    }).groupCollect((group : Customer[*]) : {title:String, customerCount : Integer} {
                        { 
                            title := group.one().title,
                            customerCount := group.size()
                        }   
                    });
                end;
            end;
            end.
        '''
        parseAndCheck(source)
        val op = getOperation('crm::Customer::countByTitle')
        val root = getStatementSourceAction(op)
        val generated = new QueryActionGenerator(repository).generateAction(root)
        AssertHelper.assertStringsEqual(
            '''
                cq
                    .groupBy(customer_.get("title"))
                    .multiselect(customer_.get("title"), cb.count(customer_))
            ''', generated.toString)
    }
    

    def void testGroupByAttributeIntoSum() throws CoreException, IOException {
        var source = '''
            model crm;
            class Customer
                attribute name : String;
                attribute title : String;
                attribute salary : Double;              
                query sumSalaryByTitle() : {title : String, totalSalary : Double} [*];
                begin
                    return Customer extent.groupBy((c : Customer) : String {
                        c.title
                    }).groupCollect((grouped : Customer[*]) : {title : String, totalSalary : Double} {
                        { 
                            title := grouped.one().title,
                            totalSalary := grouped.sum((c : Customer) : Double {
                                c.salary
                            })
                        }   
                    });
                end;
            end;
            end.
        '''
        parseAndCheck(source)
        val op = getOperation('crm::Customer::sumSalaryByTitle')
        val root = getStatementSourceAction(op)
        val generated = new QueryActionGenerator(repository).generateAction(root)
        AssertHelper.assertStringsEqual(
            '''
                cq
                    .groupBy(customer_.get("title"))
                    .multiselect(customer_.get("title"), cb.sum(customer_.get("salary")))
            ''', generated.toString)
    }
    
    def getStatementSourceAction(Operation op) {
        op.activity.rootAction.findStatements.last.sourceAction
    }

}
