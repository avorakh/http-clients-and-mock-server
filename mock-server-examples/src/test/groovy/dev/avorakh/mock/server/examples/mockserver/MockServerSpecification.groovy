package dev.avorakh.mock.server.examples.mockserver

import dev.avorakh.mock.server.examples.SpecificationWithMockServer

class MockServerSpecification extends SpecificationWithMockServer {

    def 'should be running'() {
        expect:
            mockServer.isRunning()
    }
    
    def 'should successfully get ipv4 address'() {
        when:
            def actual = mockServer.remoteAddress()
        then:
            actual != null
            actual.hostString ==~ /^(\b25[0-5]|\b2[0-4][0-9]|\b[01]?[0-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)){3}$/
    }

    def 'should successfully get port'() {
        when:
            def actual = mockServer.port
        then:
            actual != null
            actual > 0
    }
}
