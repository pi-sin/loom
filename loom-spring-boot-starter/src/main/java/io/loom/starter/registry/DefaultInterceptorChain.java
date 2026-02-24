package io.loom.starter.registry;

import io.loom.core.interceptor.InterceptorChain;
import io.loom.core.interceptor.LoomHttpContext;
import io.loom.core.interceptor.LoomInterceptor;

import java.util.List;

public class DefaultInterceptorChain implements InterceptorChain {

    private final List<LoomInterceptor> interceptors;
    private final Runnable terminalAction;
    private int index;

    public DefaultInterceptorChain(List<LoomInterceptor> interceptors, Runnable terminalAction) {
        this.interceptors = interceptors;
        this.terminalAction = terminalAction;
        this.index = 0;
    }

    @Override
    public void next(LoomHttpContext context) {
        if (index < interceptors.size()) {
            interceptors.get(index++).handle(context, this);
        } else {
            terminalAction.run();
        }
    }
}
