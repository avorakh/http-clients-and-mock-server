package dev.avorakh.mock.server.examples.callback

import dev.avorakh.mock.server.examples.SpecificationWithMockServer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.mock.action.ExpectationResponseCallback
import org.mockserver.model.Header
import org.mockserver.model.HttpClassCallback
import org.mockserver.verify.VerificationTimes

import java.net.http.HttpRequest
import java.util.concurrent.TimeUnit

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.notFoundResponse
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.JsonBody.json

class CallbackActionUsageSpecification extends SpecificationWithMockServer {

    def "should return unauthorized with error response on POST request"() {
        given:
            def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/callback"
            def requestBodyMap = [
                "username": "foo",
                "password": "bar"
            ]

            def httpRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(requestBodyMap)))
                .uri(URI.create(url))
                .header(contentType, applicationJsonCT)
                .build()
        and:
            def mockRequest = request()
            def responseBody = ["message": "incorrect username and password combination"]
        when:
            mockServer
                .when(mockRequest)
                .respond(HttpClassCallback.callback().withCallbackClass(TestExpectationResponseCallback))
            def response = sendTestRequest(httpRequest)
        then:
            mockServer.verify(mockRequest, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == unauthorized
                def actualBody = toResponseBodyMap(it)
                actualBody == responseBody
            }
        cleanup:
            mockServer.clear(mockRequest)
        where:
            unauthorized = 401
    }

    def "should successfully return ok with token response on POST request"() {
        given:
            def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/callback"
            def requestBodyMap = [
                "username": "admin",
                "password": "password"
            ]

            def httpRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(requestBodyMap)))
                .uri(URI.create(url))
                .header(contentType, applicationJsonCT)
                .build()
        and:
            def mockRequest = request()
        when:
            mockServer
                .when(mockRequest, Times.once(), TimeToLive.exactly(TimeUnit.SECONDS, 2L))
                .respond(HttpClassCallback.callback().withCallbackClass(TestExpectationResponseCallback))
            def response = sendTestRequest(httpRequest)
        then:
            mockServer.verify(mockRequest, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == ok
                def actualBody = toResponseBodyMap(it)
                actualBody.token != null
                actualBody.token ==~ /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
            }
        cleanup:
            mockServer.clear(mockRequest)
        where:
            ok = 200
    }

    def "should successfully return non found response on POST request"() {
        given:
            def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/callback/23"
        println(url)
            def requestBodyMap = [
                "username": "admin",
                "password": "password"
            ]

            def httpRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(requestBodyMap)))
                .uri(URI.create(url))
                .header(contentType, applicationJsonCT)
                .build()
        and:
            def mockRequest = request()
        when:
            mockServer
                .when(mockRequest, Times.once(), TimeToLive.exactly(TimeUnit.SECONDS, 2L))
                .respond(HttpClassCallback.callback().withCallbackClass(TestExpectationResponseCallback))
            def response = sendTestRequest(httpRequest)
        then:
            mockServer.verify(mockRequest, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == 404
            }
        cleanup:
            mockServer.clear(mockRequest)
    }

    static class TestExpectationResponseCallback implements ExpectationResponseCallback {

        @Override
        org.mockserver.model.HttpResponse handle(org.mockserver.model.HttpRequest httpRequest) throws Exception {

            if (httpRequest.getPath().getValue().endsWith("/callback")) {
                def actualBody = new JsonSlurper().parseText(httpRequest.getBodyAsJsonOrXmlString())
                def isValidCreads = actualBody.username == "admin" && actualBody.password == "password"
                if (!isValidCreads) {
                    return response()
                        .withStatusCode(401)
                        .withHeaders(new Header("content-type", "application/json"))
                        .withBody(json(Collections.singletonMap("message", "incorrect username and password combination")));
                }

                return response()
                    .withStatusCode(200)
                    .withHeaders(new Header("content-type", "application/json"))
                    .withBody(json(Collections.singletonMap("token", toUUID())))
            } else {
                return notFoundResponse();
            }
        }

        static def toUUID() {

            UUID.randomUUID().toString()
        }
    }
}
