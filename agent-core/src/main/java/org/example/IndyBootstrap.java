package org.example;


import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.StreamDrainer;

import java.io.IOException;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class IndyBootstrap {

    /**
     * Starts with {@code java.lang} so that OSGi class loaders don't restrict access to it.
     * This also allows to load it in {@code java.base} module on Java9+ for Hotspot, Open J9 requires {@code ModuleSetter}
     */
    private static final String INDY_BOOTSTRAP_CLASS_NAME = "java.lang.IndyBootstrapDispatcher";

    /**
     * The class file of {@code IndyBootstrapDispatcher}, loaded from classpath resource, {@code esclazz} extension avoids
     * being loaded as a regular class.
     */
    private static final String INDY_BOOTSTRAP_RESOURCE = "bootstrap/java/lang/IndyBootstrapDispatcher.class";

    /**
     * Needs to be loaded from the bootstrap CL because it uses {@code sun.misc.Unsafe}.
     * In addition, needs to be loaded explicitly by name only when running on Java 9, because compiled with Java 9
     */
    private static final String INDY_BOOTSTRAP_MODULE_SETTER_CLASS_NAME = "co.elastic.apm.agent.modulesetter.ModuleSetter";

    /**
     * The class file of {@code ModuleSetter}, loaded from classpath resource, {@code esclazz} extension avoids being
     * loaded as a regular class.
     */
    private static final String INDY_BOOTSTRAP_MODULE_SETTER_RESOURCE = "bootstrap/co/elastic/apm/agent/modulesetter/ModuleSetter.esclazz";

    /**
     * The name of the class we use as the lookup class during the invokedynamic bootstrap flow. The bytecode of this
     * class is injected into the plugin class loader, then loaded from that class loader and used as the lookup class
     * to link the instrumented call site to the advice method.
     */
    public static final String LOOKUP_EXPOSER_CLASS_NAME = "org.example.LookupExposer";

    /**
     * Caches the names of classes that are defined within a package and it's subpackages
     */
    private static final ConcurrentMap<String, List<String>> classesByPackage = new ConcurrentHashMap<>();

    static Method indyBootstrapMethod;

    static Method bootstrapLoggingMethod;



    public static Method getIndyBootstrapMethod() {
        if (indyBootstrapMethod != null) {
            return indyBootstrapMethod;
        }
        try {
            Class<?> indyBootstrapClass = initIndyBootstrap();
            indyBootstrapClass
                    .getField("bootstrap")
                    .set(null, IndyBootstrap.class.getMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class));
            return indyBootstrapMethod = indyBootstrapClass.getMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void destroy() {
        try {
            indyBootstrapMethod = null;
            classesByPackage.clear();
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }




    /**
     * Injects the {@code java.lang.IndyBootstrapDispatcher} class into the bootstrap class loader if it wasn't already.
     */
    private static Class<?> initIndyBootstrap() throws Exception {
        Class<?> indyBootstrapDispatcherClass = loadClassInBootstrap(INDY_BOOTSTRAP_CLASS_NAME, INDY_BOOTSTRAP_RESOURCE);

        return indyBootstrapDispatcherClass;
    }

    /**
     * Loads a class from classpath resource in bootstrap classloader.
     * <p>
     * Ensuring that classes loaded through this method can ONLY be loaded in the bootstrap CL requires the following:
     * <ul>
     *     <li>The class bytecode resource name should not end with the {@code .class} suffix</li>
     *     <li>The class bytecode resource name should be in a location that reflects its package</li>
     *     <li>For tests in IDE, the class name used here should be distinct from its original class name to ensure
     *     that only the relocated resource is being used</li>
     * </ul>
     *
     * @param className    class name
     * @param resourceName class resource name
     * @return class loaded in bootstrap classloader
     * @throws IOException            if unable to open provided resource
     * @throws ClassNotFoundException if unable to load class in bootstrap CL
     */
    private static Class<?> loadClassInBootstrap(String className, String resourceName) throws IOException, ClassNotFoundException {
        Class<?> bootstrapClass;
        try {
            // Will return non-null value only if the class has already been loaded.
            // Ensuring that a class can ONLY be loaded through this method and not from regular classloading relies
            // on applying the listed instructions in method documentation
            bootstrapClass = Class.forName(className, false, null);
        } catch (ClassNotFoundException e) {
            byte[] classBytes = StreamDrainer.DEFAULT.drain(AgentMain.class.getClassLoader().getResourceAsStream(resourceName));
//            byte[] classBytes = IOUtils.readToBytes(ElasticApmAgent.getAgentClassLoader().getResourceAsStream(resourceName));
            if (classBytes == null || classBytes.length == 0) {
                throw new IllegalStateException("Could not locate " + resourceName);
            }
            ClassInjector.UsingUnsafe.ofBootLoader().injectRaw(Collections.singletonMap(className, classBytes));
            bootstrapClass = Class.forName(className, false, null);
        }
        return bootstrapClass;
    }


    /**
     * A package-private method for unit-testing of the module overriding functionality
     *
     * @param targetClass class for which module should be overridden with the {@code java.base} module
     * @throws Throwable in case of any failure related to module overriding
     */
    static void setJavaBaseModule(Class<?> targetClass) throws Throwable {
        // In order to override the original unnamed module assigned to the IndyBootstrapDispatcher, we rely on the
        // Unsafe API, which requires the caller to be loaded by the Bootstrap CL

        Class<?> moduleSetterClass = loadClassInBootstrap(INDY_BOOTSTRAP_MODULE_SETTER_CLASS_NAME, INDY_BOOTSTRAP_MODULE_SETTER_RESOURCE);
        MethodHandles.lookup()
                .findStatic(moduleSetterClass, "setJavaBaseModule", MethodType.methodType(void.class, Class.class))
                .invoke(targetClass);
    }

    public static void logExceptionThrownByAdvice(Throwable exception) {
        System.err.println("Advice threw an exception, this should never happen!");
        exception.printStackTrace();
    }

    /**
     * Is called by {@code java.lang.IndyBootstrapDispatcher#bootstrap} via reflection.
     * <p>
     * This is to make it impossible for OSGi or other filtering class loaders to restrict access to classes in the bootstrap class loader.
     * Normally, additional classes that have been injected have to be explicitly allowed via the {@code org.osgi.framework.bootdelegation}
     * system property.
     * But because we inject our class directly in the {@code java.lang} package which has to be on the allow list of filtering class loaders
     * we can be sure that any other class can always call that class.
     * </p>
     * <p>
     * This method gets called the first time an instrumented method is called.
     * On instrumentation an {@code invokedynamic} instruction is inserted that delays the linking to the target method until runtime.
     * The linking to the target method is done in this method.
     * </p>
     * <p>
     * If not already created, the lookup of the target method creates a dedicated class loader for the classes in the plugin that has
     * instrumented the {@code instrumentedType}.
     * Via package scanning it finds all the classes that are in the package of the advice that was responsible for the instrumentation.
     * It then links to the advice loaded from the plugin class loader.
     * The advice can access both agent types and the types of the instrumented library.
     * </p>
     * <p>
     * Exceptions and {@code null} return values are handled by {@code java.lang.IndyBootstrapDispatcher#bootstrap}.
     * </p>
     * This is how a bootstrap method looks like in the class file:
     * <pre>
     * BootstrapMethods:
     *   1: #1060 REF_invokeStatic java/lang/IndyBootstrapDispatcher.bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;
     *     Method arguments:
     *       #1049 co.elastic.apm.agent.bci.InstrumentationTest$CommonsLangInstrumentation
     *       #1050 0
     *       #12 org/apache/commons/lang3/StringUtils
     *       #1072 isNotEmpty
     *       #1075 REF_invokeStatic org/apache/commons/lang3/StringUtils.isNotEmpty:(Ljava/lang/CharSequence;)Z
     * </pre>
     *
     * And this is how a invokedynamic instruction looks like inside methods,
     * referencing above bootstrap method
     * <pre>
     *     invokedynamic #1076,  0           // InvokeDynamic #1:onEnter:()V
     * </pre>
     *
     * @param lookup           A {@code java.lang.invoke.MethodHandles.Lookup} representing the instrumented method.
     * @param adviceMethodName A {@link String} representing the advice method name.
     * @param adviceMethodType A {@link java.lang.invoke.MethodType} representing the arguments and return type of the advice method.
     * @param args             Additional arguments that are provided by Byte Buddy:
     *                         <ul>
     *                           <li>A {@link String} of the binary advice class name.</li>
     *                           <li>A {@link int} with value {@code 0} for an enter advice and {code 1} for an exist advice.</li>
     *                           <li>A {@link Class} representing the class implementing the instrumented method.</li>
     *                           <li>A {@link String} with the name of the instrumented method.</li>
     *                           <li>A {@link java.lang.invoke.MethodHandle} representing the instrumented method unless the target is the type's static initializer.</li>
     *                         </ul>
     * @return a {@link ConstantCallSite} that is the target of the invokedynamic
     */
    public static ConstantCallSite bootstrap(final MethodHandles.Lookup lookup,
                                             final String adviceMethodName,
                                             final MethodType adviceMethodType,
                                             final Object... args) {

        if (System.getSecurityManager() == null) {
            return internalBootstrap(lookup, adviceMethodName, adviceMethodType, args);
        }

        // callsite resolution needs privileged access to call Class#getClassLoader() and MethodHandles$Lookup#findStatic
        return AccessController.doPrivileged(new PrivilegedAction<ConstantCallSite>() {
            @Override
            public ConstantCallSite run() {
                return internalBootstrap(lookup, adviceMethodName, adviceMethodType, args);
            }
        });
    }

    private static ConstantCallSite internalBootstrap(MethodHandles.Lookup lookup,
                                                      String adviceMethodName,
                                                      MethodType adviceMethodType, Object[] args) {
        try {
            String adviceClassName = (String) args[0];
            int enter = (Integer) args[1];
            Class<?> instrumentedType = (Class<?>) args[2];
            String instrumentedMethodName = (String) args[3];
            MethodHandle instrumentedMethod = args.length >= 5 ? (MethodHandle) args[4] : null;

            ClassLoader instrumentationClassLoader = AgentMain.class.getClassLoader();
            ClassLoader targetClassLoader = lookup.lookupClass().getClassLoader();
            ClassFileLocator classFileLocator;
            List<String> pluginClasses = new ArrayList<>();
            Map<String, List<String>> requiredModuleOpens = Collections.emptyMap();


//            classFileLocator = ClassFileLocator.ForClassLoader.of(instrumentationClassLoader);
//            String pluginPackage = "org.example.advices.indy";
//            pluginClasses.addAll(getClassNamesFromBundledPlugin(pluginPackage, instrumentationClassLoader));
//
//            pluginClasses.add(LOOKUP_EXPOSER_CLASS_NAME);
//            ClassLoader pluginClassLoader = IndyPluginClassLoaderFactory.getOrCreatePluginClassLoader(
//                    targetClassLoader,
//                    pluginClasses,
//                    // we provide the instrumentation class loader as the agent class loader, but it could actually be an
//                    // ExternalPluginClassLoader, of which parent is the agent class loader, so this works as well.
//                    instrumentationClassLoader,
//                    classFileLocator,
//                    ElementMatchers.none());
            ClassLoader pluginClassLoader = instrumentationClassLoader;


//            Class<?> adviceInPluginCL = pluginClassLoader.loadClass(adviceClassName);
//            Class<LookupExposer> lookupExposer = (Class<LookupExposer>) pluginClassLoader.loadClass(LOOKUP_EXPOSER_CLASS_NAME);

            Class<?> adviceInPluginCL = pluginClassLoader.loadClass(adviceClassName);
            Class<LookupExposer> lookupExposer = (Class<LookupExposer>) pluginClassLoader.loadClass(LOOKUP_EXPOSER_CLASS_NAME);
            // can't use MethodHandle.lookup(), see also https://github.com/elastic/apm-agent-java/issues/1450
            MethodHandles.Lookup indyLookup = (MethodHandles.Lookup) lookupExposer.getMethod("getLookup").invoke(null);
            // When calling findStatic now, the lookup class will be one that is loaded by the plugin class loader
            MethodHandle methodHandle = indyLookup.findStatic(adviceInPluginCL, adviceMethodName, adviceMethodType);
            return new ConstantCallSite(methodHandle);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private static List<String> getClassNamesFromBundledPlugin(String pluginPackage, ClassLoader adviceClassLoader) throws IOException, URISyntaxException {
        List<String> pluginClasses = classesByPackage.get(pluginPackage);
        if (pluginClasses == null) {
            pluginClasses = new ArrayList<>();
            Collection<String> pluginClassLoaderRootPackages = Collections.singletonList(pluginPackage);
            for (String pkg : pluginClassLoaderRootPackages) {
                pluginClasses.addAll(PackageScanner.getClassNames(pkg, adviceClassLoader));
            }
            classesByPackage.putIfAbsent(pluginPackage, pluginClasses);
            pluginClasses = classesByPackage.get(pluginPackage);
        }
        return pluginClasses;
    }

}
