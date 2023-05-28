package dev.avorakh.mock.server.examples

import groovy.json.JsonSlurper
import org.mockserver.integration.ClientAndServer
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class SpecificationWithMockServer extends Specification {
    @Shared
    def contentType = "content-type"
    @Shared
    def applicationJsonCT = "application/json"
    
    @Shared
    HttpClient testHttpClient
    @Shared
    ClientAndServer mockServer

    def setupSpec() {
        testHttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()

        mockServer = ClientAndServer.startClientAndServer()
    }

    def cleanupSpec() {
        mockServer.stop()
    }

    def generateRandomUUID() {
        UUID.randomUUID().toString()
    }

    def sendTestRequest(HttpRequest request) {
        testHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    def toResponseBodyMap(HttpResponse<String> response) {
        new JsonSlurper().parseText(response.body()) as Map
    }
}
