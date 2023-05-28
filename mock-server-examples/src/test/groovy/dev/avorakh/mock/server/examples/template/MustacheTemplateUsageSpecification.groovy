package dev.avorakh.mock.server.examples.template

import dev.avorakh.mock.server.examples.SpecificationWithMockServer
import org.mockserver.matchers.Times
import org.mockserver.model.HttpTemplate
import org.mockserver.verify.VerificationTimes

import java.net.http.HttpRequest
import java.util.concurrent.TimeUnit

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpTemplate.template
import static spock.util.matcher.HamcrestSupport.that

class MustacheTemplateUsageSpecification extends SpecificationWithMockServer {

    def "should successfully return '#aStatusCode' response code with '#errorCode' and '#errorMessage'"(
        int aStatusCode,
        int errorCode,
        String errorMessage
    ) {
        given:
            def mockRequest = request()
                .withMethod("GET")
                .withPath("/mustache")

            def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/mustache"
            def aTemplate = """\
                |{
                |   'statusCode': $aStatusCode,
                |   'headers': {
                |       'content-type': [ 'application/json'],
                |       'date' : '{{ now_rfc_1123 }}',
                |       'Request-Id' : '{{ uuid }}',
                |       'X-ERROR-CODE': '$errorCode',
                |       'X-ERROR-MESSAGE': '$errorMessage'
                |   },
                |   'body': \"{\\\"code\\\":\\\"$errorCode\\\",\\\"message\\\":\\\"$errorMessage\\\"}\"
                |}
            """.stripMargin()

            def request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .header(contentType, applicationJsonCT)
                .build()
        when:
            mockServer.openUI(TimeUnit.MINUTES, 2L)
                .when(mockRequest, Times.once())
                .respond(template(HttpTemplate.TemplateType.MUSTACHE, aTemplate))
            def response = sendTestRequest(request)
        then:
            mockServer.verify(mockRequest, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == aStatusCode
                with(headers().map()) {
                    containsKey('content-type')
                    that get('content-type'), org.hamcrest.Matchers.containsInAnyOrder("application/json")
                    containsKey('date')
                    containsKey('request-id')
                    containsKey('x-error-code')
                    that get('x-error-code'), org.hamcrest.Matchers.containsInAnyOrder(String.valueOf(errorCode))
                    containsKey('x-error-message')
                    that get('x-error-message'), org.hamcrest.Matchers.containsInAnyOrder(errorMessage)
                }
                def bodyMap = toResponseBodyMap(it)
                bodyMap.code == String.valueOf(errorCode)
                bodyMap.message == errorMessage
            }
        cleanup:
            mockServer.clear(mockRequest)
        where:
            aStatusCode | errorCode | errorMessage
            400         | 1000      | "Validation Error"
            401         | 1001      | "Authorisation Error"
            500         | 2000      | "Internal Error"
    }
}
