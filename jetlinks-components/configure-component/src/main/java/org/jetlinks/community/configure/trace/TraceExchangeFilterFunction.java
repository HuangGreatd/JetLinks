package org.jetlinks.community.configure.trace;

import org.jetlinks.core.trace.TraceHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

public final class TraceExchangeFilterFunction implements ExchangeFilterFunction {

    private static final TraceExchangeFilterFunction INSTANCE = new TraceExchangeFilterFunction();


    public static ExchangeFilterFunction instance() {
        return INSTANCE;
    }

    private TraceExchangeFilterFunction() {
    }

    @Override
    @Nonnull
    public Mono<ClientResponse> filter(@Nonnull ClientRequest request,
                                       @Nonnull ExchangeFunction next) {
        return TraceHolder
            .writeContextTo(ClientRequest.from(request), ClientRequest.Builder::header)
            .flatMap(builder -> next.exchange(builder.build()));
    }

}
