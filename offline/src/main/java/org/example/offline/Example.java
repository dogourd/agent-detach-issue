package org.example.offline;

import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.utility.StreamDrainer;
import org.example.MyURLClassLoader;

import java.io.File;
import java.io.InputStream;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <h1> Example </h1>
 *
 * @author yuwen
 * @since 2023-04-24
 */
public class Example {

    public static void main(String[] args) throws Throwable {
        loadAndUnloadIndy();
//        loadAndUnload();
        waiting();
    }

    static void loadAndUnloadIndy() throws Throwable {
        File jarFile = new File("./agent-core/target/agent-core-1.0-SNAPSHOT.jar");
        MyURLClassLoader myClassLoader = new MyURLClassLoader(new URL[]{jarFile.toURI().toURL()}, null);

        String[] classes = {"org.example.LookupExposer", "org.example.advices.indy.HttpServletIndyAdvice"};
        Map<String, byte[]> byteRepresentation = new LinkedHashMap<>();
        for (String klass : classes) {
            try (InputStream resource = myClassLoader.getResourceAsStream(klass.replace('.', '/') + ".class");) {
                byte[] bytes = StreamDrainer.DEFAULT.drain(resource);
                byteRepresentation.put(klass, bytes);
            }
        }

        ClassLoader classLoader = new ByteArrayClassLoader.ChildFirst(null, true, byteRepresentation);

        Class<?> exposer = classLoader.loadClass("org.example.LookupExposer");
        MethodHandles.Lookup lookup = (MethodHandles.Lookup) exposer.getDeclaredMethod("getLookup").invoke(null);

        Class<?> adviceClass = classLoader.loadClass("org.example.advices.indy.HttpServletIndyAdvice");
        MethodType methodType = MethodType.methodType(void.class);
        MethodHandle methodHandle = lookup.findStatic(adviceClass, "before", methodType);
        ConstantCallSite callSite = new ConstantCallSite(methodHandle);
        callSite.getTarget().invokeExact();

        myClassLoader.close();
    }

    static void loadAndUnload() throws Throwable {
        File jarFile = new File("./agent-core/target/agent-core-1.0-SNAPSHOT.jar");
        MyURLClassLoader classLoader = new MyURLClassLoader(new URL[]{jarFile.toURI().toURL()}, null);
        Class<?> exposer = classLoader.loadClass("org.example.LookupExposer");
        Object lookup = exposer.getDeclaredMethod("getLookup").invoke(null);
        System.out.println(lookup);

        classLoader.close();
    }

    static void waiting() throws Throwable {
        System.in.read();
    }

}
