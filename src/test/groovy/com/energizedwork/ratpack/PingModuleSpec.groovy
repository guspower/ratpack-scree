package com.energizedwork.ratpack

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.multibindings.Multibinder
import ratpack.groovy.handling.GroovyChainAction
import ratpack.groovy.server.GroovyRatpackServerSpec
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.guice.BindingsSpec
import ratpack.guice.ConfigurableModule
import ratpack.guice.Guice
import ratpack.handling.Handler
import ratpack.handling.HandlerDecorator
import ratpack.handling.Handlers
import ratpack.http.client.ReceivedResponse
import ratpack.registry.Registry
import ratpack.test.CloseableApplicationUnderTest
import ratpack.test.http.TestHttpClient
import spock.lang.Specification
import spock.lang.Unroll

class PingModuleSpec extends Specification {

    CloseableApplicationUnderTest aut
    @Delegate TestHttpClient client

    def setup() {
        aut = GroovyEmbeddedApp.of { GroovyRatpackServerSpec server ->
            server.registry(Guice.registry { BindingsSpec bindings ->
                bindings.module PingModule2
            })
            server.handlers {
                get { render 'OK' }
            }
        }
        client = TestHttpClient.testHttpClient(aut)
    }
    def cleanup() { aut.close() }

    @Unroll("can use ping module on path [#path]")
    def "can use ping module on path"() {
        when:
            ReceivedResponse response = get ''

        then:
            200 == response.statusCode

        where:
            path = PingModuleConfig.DEFAULT_PATH
    }

}

final class Ping extends GroovyChainAction {

    final static String MESSAGE = 'Ratpack rules!'

    final String pingPathBinding

    @Inject
    Ping(final PingModuleConfig config) {
        pingPathBinding = config.path
    }

    @Override
    void execute() throws Exception {
        path(pingPathBinding) {
            render MESSAGE
        }
    }
}

final class PingModuleConfig {
    final static String DEFAULT_PATH = 'ping'

    String path = DEFAULT_PATH
}

final class PingModule extends ConfigurableModule<PingModuleConfig> {

    @Override
    protected void configure() {
        bind Ping

        final Provider<Ping> pingProvider = getProvider(Ping)

        Multibinder
            .newSetBinder(binder(), HandlerDecorator)
            .addBinding()
            .toProvider({ ->
            { Registry registry, Handler rest ->
                Handlers.chain(rest, Handlers.chain(registry, pingProvider.get()))
            } as HandlerDecorator
        } as Provider<HandlerDecorator>)
    }

}

final class PingHandlerDecorator implements HandlerDecorator {

    private final Provider<Ping> pingProvider

    @Inject
    PingHandlerDecorator(Provider<Ping> pingProvider) {
        this.pingProvider = pingProvider
    }

    @Override
    Handler decorate(Registry registry, Handler rest) throws Exception {
        Handlers.chain rest, Handlers.chain(registry, pingProvider.get())
    }

}

final class PingModule2 extends ConfigurableModule<PingModuleConfig> {

    @Override
    protected void configure() {
        bind Ping
        bind PingHandlerDecorator
    }

}
