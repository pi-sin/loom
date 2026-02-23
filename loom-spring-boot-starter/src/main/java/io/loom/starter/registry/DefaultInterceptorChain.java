package io.loom.starter.registry;

import io.loom.core.interceptor.InterceptorChain;
import io.loom.core.interceptor.LoomHttpContext;
import io.loom.core.interceptor.LoomInterceptor;

import java.util.List;

public class DefaultInterceptorChain implements InterceptorChain {

    private final List<LoomInterceptor> interceptors;
    private final int index;
    private final Runnable terminalAction;

    public DefaultInterceptorChain(List<LoomInterceptor> interceptors, Runnable terminalAction) {
        this(interceptors, 0, terminalAction);
    }

    private DefaultInterceptorChain(List<LoomInterceptor> interceptors, int index, Runnable terminalAction) {
        this.interceptors = interceptors;
        this.index = index;
        this.terminalAction = terminalAction;
    }

    @Override
    public void next(LoomHttpContext context) {
        if (index < interceptors.size()) {
            LoomInterceptor current = interceptors.get(index);
            DefaultInterceptorChain next = new DefaultInterceptorChain(interceptors, index + 1, terminalAction);
            current.handle(context, next);
        } else {
            terminalAction.run();
        }
    }
}
