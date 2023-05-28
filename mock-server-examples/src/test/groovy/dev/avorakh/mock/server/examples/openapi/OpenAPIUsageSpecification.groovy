package dev.avorakh.mock.server.examples.openapi

import dev.avorakh.mock.server.examples.SpecificationWithMockServer
import groovy.json.JsonOutput
import org.mockserver.mock.Expectation
import org.mockserver.model.HttpTemplate
import org.mockserver.verify.VerificationTimes
import spock.lang.Shared
import spock.lang.Unroll

import java.net.http.HttpRequest

import static org.mockserver.mock.OpenAPIExpectation.openAPIExpectation
import static org.mockserver.model.HttpTemplate.template
import static org.mockserver.model.OpenAPIDefinition.openAPI
import static spock.util.matcher.HamcrestSupport.that

class OpenAPIUsageSpecification extends SpecificationWithMockServer {

    @Shared
    def xRequestIDHeader = 'X-Request-ID'
    @Shared
    String openapi

    def setupSpec() {

        def classLoader = this.getClass().classLoader
        openapi = classLoader.getResourceAsStream("openapi/openapi_petstore.yaml").text
    }

    def "should successfully get pet by id"() {
        when:
            def expectationId = upsertOpenAPIOperationAndGetExpectationId("showPetById", "200")
            def response = sendTestRequest(getShowPetByIdRequest())
        then:
            mockServer.verify(expectationId, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == 200
                def actualBody = toResponseBodyMap(it)
                actualBody.id == 2
                actualBody.name == 'Crumble'
                actualBody.tag == 'dog'
            }
        cleanup:
            mockServer.clear(expectationId)
    }

    def "should return internal server error on get pet by id"() {
        when:
            def expectationId = upsertOpenAPIOperationAndGetExpectationId("showPetById", "500")
            def response = sendTestRequest(getShowPetByIdRequest())
        then:
            mockServer.verify(expectationId, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == 500
                def actualBody = toResponseBodyMap(it)
                actualBody.containsKey('code')
                actualBody.containsKey('message')
            }
        cleanup:
            mockServer.clear(expectationId)
    }

    def "should successfully create pet"() {
        when:
            def expectationId = upsertOpenAPIOperationAndGetExpectationId("createPets", "201")
            def response = sendTestRequest(getCreatePetsRequest())
        then:
            mockServer.verify(expectationId, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == 201
                body().empty
            }
        cleanup:
            mockServer.clear(expectationId)
    }

    def "should return BAD REQUEST on creating pet"() {
        when:
            def expectationId = upsertOpenAPIOperationAndGetExpectationId("createPets", "400")
            def response = sendTestRequest(getCreatePetsRequest())
        then:
            mockServer.verify(expectationId, VerificationTimes.once())
        then:
            response != null
            println response.statusCode()
            println response.headers()
            println response.body()
            with(response) {
                statusCode() == 400
                def actualBody = toResponseBodyMap(it)
                actualBody.containsKey('code')
                actualBody.containsKey('message')
            }
        cleanup:
            mockServer.clear(expectationId)
    }

    @Unroll
    def "should successfully return '#aStatusCode' response code with '#errorCode' and '#errorMessage' on getting List pets"(
        int aStatusCode,
        int errorCode,
        String errorMessage
    ) {

        given:
            def mockRequest = openAPI(openapi, "listPets")
            def aTemplate = """\
                |{
                |   'statusCode': $aStatusCode,
                |   'headers': {
                |       'content-type': [ 'application/json'],
                |       'timestamp' : '\$!date',
                |       'timeZone' : '\$!date.timeZone.ID',
                |       'x-code': '$errorCode'
                |   },
                |   'body': \"{\\\"code\\\":$errorCode,\\\"message\\\":\\\"$errorMessage\\\"}\"
                |}
            """.stripMargin()
        when:
            mockServer
                .when(mockRequest)
                .respond(template(HttpTemplate.TemplateType.VELOCITY, aTemplate))
            def response = sendTestRequest(getListPetsRequest())
        then:
            mockServer.verify(mockRequest, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == aStatusCode
                with(headers().map()) {
                    containsKey('content-type')
                    that get('content-type'), org.hamcrest.Matchers.containsInAnyOrder("application/json")
                    containsKey('timestamp')
                    containsKey('timeZone')
                    containsKey('x-code')
                    that get('x-code'), org.hamcrest.Matchers.containsInAnyOrder(String.valueOf(errorCode))
                }
                def bodyMap = toResponseBodyMap(it)
                bodyMap.code == errorCode
                bodyMap.message == errorMessage
            }
            mockServer.clear(mockRequest)
        where:
            aStatusCode | errorCode | errorMessage
            400         | 1000      | "Validation Error"
            401         | 1001      | "Authorisation Error"
            500         | 2000      | "Internal Error"
    }

    def extractExpectationId(Expectation[] expectations) {
        if (expectations == null || expectations.size() != 1) {
            return null
        }
        return expectations[0].id
    }

    def upsertOpenAPIOperationAndGetExpectationId(String operationId, String statusCode) {
        def operationAndResponseMap = Map.of(operationId, statusCode)
        def expectations = mockServer.upsert(openAPIExpectation(openapi, operationAndResponseMap))
        return extractExpectationId(expectations)
    }

    def getShowPetByIdRequest() {
        def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/pets/5"
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(url))
            .header(contentType, applicationJsonCT)
            .header(xRequestIDHeader, generateRandomUUID())
            .build()
    }

    def getListPetsRequest() {
        def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/pets"
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(url))
            .header(contentType, applicationJsonCT)
            .build()
    }

    def getCreatePetsRequest() {
        def requestBody = [
            "id"  : 2,
            "name": "Crumble",
            "tag" : "dog"
        ]

        def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/pets"

        def requestBodyJson = JsonOutput.toJson(requestBody)

        HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
            .uri(URI.create(url))
            .header(contentType, applicationJsonCT)
            .build()
    }
}
