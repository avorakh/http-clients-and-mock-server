package dev.avorakh.mock.server.examples.requestmatcher

import dev.avorakh.mock.server.examples.SpecificationWithMockServer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.mockserver.matchers.MatchType
import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.model.Header
import org.mockserver.model.HttpStatusCode
import org.mockserver.verify.VerificationTimes
import spock.lang.Unroll

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.JsonBody.json

class RequestMatchersUsageSpecification extends SpecificationWithMockServer {

    def "should successfully return response on POST request"() {
        given:
            def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/validate"
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
            def contentTypeHeader = new Header(contentType, applicationJsonCT)
            def mockRequest = request()
                .withMethod("POST")
                .withPath("/validate")
                .withHeader(contentTypeHeader)
                .withBody(json(requestBodyMap))

            def responseBody = ["message": "incorrect username and password combination"]

            def unauthorized = 401
            def mockResponse = response()
                .withStatusCode(unauthorized)
                .withHeaders(contentTypeHeader)
                .withBody(json(responseBody))
                .withDelay(TimeUnit.SECONDS, 3)
        when:
            mockServer
                .when(mockRequest, Times.once(), TimeToLive.exactly(TimeUnit.SECONDS, 2L))
                .respond(mockResponse)
            def response = sendTestRequest(httpRequest)
        then:
            mockServer.verify(mockRequest, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == unauthorized
                def actualBody = new JsonSlurper().parseText(body())
                actualBody == responseBody
            }
    }

    @Unroll
    def "should successfully match request using path and priority for #httpMethod"(String httpMethod) {
        given:
            def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/some/path"
            def httpRequest = HttpRequest.newBuilder()
                .method(httpMethod, HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(url))
                .header(contentType, applicationJsonCT)
                .build()
        and:
            def responseBodyMap = [
                success: true,
                code   : okStatusCode,
                message: "OK"
            ]
            def mockRequest = request().withPath("/some/path")
            def contentTypeHeader = new Header(contentType, applicationJsonCT)
            def mockResponse = response()
                .withStatusCode(okStatusCode)
                .withBody(json(responseBodyMap))
                .withHeaders(contentTypeHeader)
        when:
            mockServer
                .when(
                    mockRequest,
                    Times.exactly(1),
                    TimeToLive.exactly(TimeUnit.SECONDS, 5L),
                    10
                )
                .respond(mockResponse)
            def response = sendTestRequest(httpRequest)
        then:
            mockServer.verify(mockRequest, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == okStatusCode
                def actualBody = new JsonSlurper().parseText(body())
                actualBody == responseBodyMap
            }
        cleanup:
            mockServer.clear(mockRequest)
        where:
            okStatusCode = 200
            httpMethod << ["GET", "POST", "PUT", "DELETE"]
    }

    def "should successfully match request by path exactly twice"() {
        given:
            def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/exactly/twice"

            def httpRequest = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(url))
                .header(contentType, applicationJsonCT)
                .build()
        and:
            def mockRequest = request().withPath("/exactly/twice")
            def contentTypeHeader = new Header(contentType, applicationJsonCT)
            def mockResponse = response()
                .withStatusCode(noContentStatusCode)
                .withHeaders(contentTypeHeader)
        when:
            mockServer
                .when(
                    mockRequest,
                    Times.exactly(2),
                    TimeToLive.exactly(TimeUnit.SECONDS, 5L),
                    10
                )
                .respond(mockResponse)
            def response1 = testHttpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding())
            def response2 = testHttpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding())
        then:
            mockServer.verify(mockRequest, VerificationTimes.exactly(2))
        then:
            response1 != null
            with(response1) {
                statusCode() == noContentStatusCode
            }
            response2 != null
            with(response2) {
                statusCode() == noContentStatusCode
            }
        cleanup:
            mockServer.clear(mockRequest)
        where:
            noContentStatusCode = 204
    }

    def "should successfully match request by path and body with json ignoring extra fields "() {
        given:
            def url = "http://${mockServer.remoteAddress().hostString}:${mockServer.port}/accepted"

            def id = generateRandomUUID()
            def context1 = [
                source   : generateRandomUUID(),
                timestamp: System.currentTimeMillis()
            ]
            def context2 = [
                source   : generateRandomUUID(),
                timestamp: System.currentTimeMillis(),
                error    : 2032
            ]
            def contexts = [context1, context2]
            def tags = ["smart", "home", "iot"]
            def requestBodyMap = [
                "_type" : "log",
                "id"    : id,
                "tags"  : tags,
                success : true,
                contexts: contexts
            ]
            def httpRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(requestBodyMap)))
                .uri(URI.create(url))
                .header(contentType, applicationJsonCT)
                .build()
        and:
            def mockRequestBodyMap = [
                "_type" : "log",
                "id"    : id,
                success : true,
                contexts: contexts
            ]

            def contentTypeHeader = new Header(contentType, applicationJsonCT)
            def mockRequest = request()
                .withMethod("POST")
                .withPath("/accepted")
                .withHeader(contentTypeHeader)
                .withBody(json(mockRequestBodyMap, MatchType.ONLY_MATCHING_FIELDS))

            def mockResponse = response()
                .withStatusCode(accepted)
        when:
            mockServer
                .when(
                    mockRequest,
                    Times.exactly(1),
                    TimeToLive.exactly(TimeUnit.SECONDS, 5L)
                )
                .respond(mockResponse)
            def response = testHttpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding())
        then:
            mockServer.verify(mockRequest, VerificationTimes.once())
        then:
            response != null
            with(response) {
                statusCode() == accepted
            }
        where:
            accepted = HttpStatusCode.ACCEPTED_202.code()
    }
}
