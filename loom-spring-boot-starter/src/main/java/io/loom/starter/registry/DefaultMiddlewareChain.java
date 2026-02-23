package io.loom.starter.registry;

import io.loom.core.middleware.LoomHttpContext;
import io.loom.core.middleware.Middleware;
import io.loom.core.middleware.MiddlewareChain;

import java.util.List;

public class DefaultMiddlewareChain implements MiddlewareChain {

    private final List<Middleware> middlewares;
    private final int index;
    private final Runnable terminalAction;

    public DefaultMiddlewareChain(List<Middleware> middlewares, Runnable terminalAction) {
        this(middlewares, 0, terminalAction);
    }

    private DefaultMiddlewareChain(List<Middleware> middlewares, int index, Runnable terminalAction) {
        this.middlewares = middlewares;
        this.index = index;
        this.terminalAction = terminalAction;
    }

    @Override
    public void next(LoomHttpContext context) {
        if (index < middlewares.size()) {
            Middleware current = middlewares.get(index);
            DefaultMiddlewareChain next = new DefaultMiddlewareChain(middlewares, index + 1, terminalAction);
            current.handle(context, next);
        } else {
            terminalAction.run();
        }
    }
}
