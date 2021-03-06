package no.fk.fint.employee

import no.skate.Identifikator
import spock.lang.Specification

class MockEmployeeServiceSpec extends Specification {

    private MockEmployeeService mockEmployeeService

    void setup() {
        mockEmployeeService = new MockEmployeeService()
    }

    def "Get all employees"() {
        when:
        def employees = mockEmployeeService.getEmployees("test-orgId")

        then:
        employees.size() == 5
    }

    def "Get employees with name"() {
        when:
        def employees = mockEmployeeService.getEmployees("test-orgId", "line")

        then:
        employees.size() == 1
        employees.get(0).getNavn().getFornavn() == "Line"
    }

    def "Get employee by identifikatortype and id"() {
        when:
        def employee = mockEmployeeService.getEmployee("test-orgId", new Identifikator("fodselsnummer", "12345678901"))

        then:
        employee.getNavn().getFornavn() == "Ole"
    }

    def "Update email address of employee"() {
        given:
        def employee = mockEmployeeService.getEmployees("test-orgId")[0]
        employee.getKontaktinformasjon().setEpostadresse("test@test.com")

        when:
        def response = mockEmployeeService.updateEmployee("test-orgId", employee)
        def existingEmployee = mockEmployeeService.getEmployee("test-orgId", employee.getIdentifikatorer().get(0))

        then:
        response.getStatus() == "ok"
        existingEmployee.getKontaktinformasjon().getEpostadresse() == "test@test.com"
    }
}
