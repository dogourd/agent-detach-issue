package org.example;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.pool.TypePool;
import org.example.advices.HttpServletAdvice;
import org.example.advices.HttpServletIndyAdvice;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;


public class AgentMain {

    private static Instrumentation inst;
    private static ResettableClassFileTransformer transformer;


    public static void detach() throws Throwable {
        if (transformer != null) {
            System.err.println("de-instrument");
            transformer.reset(
                    inst, transformer,
                    AgentBuilder.RedefinitionStrategy.RETRANSFORMATION,
                    AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE,
                    AgentBuilder.RedefinitionStrategy.BatchAllocator.ForTotal.INSTANCE,
                    AgentBuilder.RedefinitionStrategy.Listener.StreamWriting.toSystemError()
            );

            transformer = null;
            inst = null;

            IndyPluginClassLoaderFactory.clear();
            IndyBootstrap.destroy();
        }
    }

    public static void init(String arg, Instrumentation inst) {
        boolean indyEnabled = Boolean.getBoolean("agent.indy.enabled");
        AgentMain.inst = inst;
        ByteBuddy byteBuddy = new ByteBuddy().with(MethodGraph.Compiler.ForDeclaredMethods.INSTANCE);
        transformer = new AgentBuilder.Default(byteBuddy)
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.RedefinitionStrategy.Listener.StreamWriting.toSystemError())
                .disableClassFormatChanges()
                .type(named("org.springframework.web.servlet.FrameworkServlet"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {

                    System.err.println("On Transformation: " + typeDescription.getName());
                    ClassFileLocator locator = new ClassFileLocator.Compound(
                            ClassFileLocator.ForClassLoader.of(AgentMain.class.getClassLoader()),
                            ClassFileLocator.ForClassLoader.ofSystemLoader(),
                            ClassFileLocator.ForClassLoader.of(classLoader)
                    );

                    TypePool typePool = AgentBuilder.PoolStrategy.Default.FAST.typePool(locator, classLoader);
                    Advice.WithCustomMapping adviceBuilder = Advice.withCustomMapping();
                    Class<?> adviceClass;
                    if (indyEnabled) {
                        adviceBuilder = adviceBuilder.bootstrap(IndyBootstrap.getIndyBootstrapMethod());
                        adviceClass = HttpServletIndyAdvice.class;
                    } else {
                        adviceClass = HttpServletAdvice.class;
                    }
                    AsmVisitorWrapper.ForDeclaredMethods visitor = adviceBuilder
                            .to(typePool.describe(adviceClass.getName()).resolve(), locator)
                            .on(named("service")
                                    .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
                                    .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
                            );

                    builder = builder.visit(visitor);
                    return builder;
                })

                .installOn(inst);
    }


}