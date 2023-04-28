package org.example;


import java.io.File;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Premain {

    static AtomicInteger sequencer = new AtomicInteger(0);
    static AtomicBoolean started = new AtomicBoolean(false);
    private static Class<?> agentClass;
    private static URLClassLoader agentClassLoader;


    private static void attachASM(String args, Instrumentation instrumentation, String agentFile) throws Throwable {
        if (agentClassLoader != null) {
            return;
        }
        File basicAgent = new File(agentFile);
        String basicAgentName = basicAgent.getName();
        String namePrefix = basicAgentName.substring(0, basicAgentName.lastIndexOf('.'));

        try (InputStream in = Files.newInputStream(basicAgent.toPath())) {
            Path agentPath = Paths.get(basicAgent.getParent(), namePrefix + "-" + sequencer.incrementAndGet() + ".jar");
            Files.copy(in, agentPath);
            System.err.println("Use agent: " + agentPath.toAbsolutePath());
            agentClassLoader = new MyURLClassLoader(new URL[]{agentPath.toUri().toURL()}, null);
        }
        try {
            agentClass = Class.forName("org.example.ASMAgentMain", true, agentClassLoader);
            agentClass.getDeclaredMethod("init", String.class, Instrumentation.class)
                    .invoke(null, args, instrumentation);
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    private static void detach() {
        try {
            if (agentClass == null) {
                return;
            }
            agentClass.getDeclaredMethod("detach")
                    .invoke(null);
            System.err.println("release agent class.");
            agentClass = null;
            System.err.println("close agent class loader.");
            agentClassLoader.close();

            Field field = Class.forName("java.lang.IndyBootstrapDispatcher", false, null)
                    .getField("bootstrap");
            field.set(null, null);
            field = null;

//            Field field = ClassLoader.class.getDeclaredField("packages");
//            field.setAccessible(true);
//            Map packages = (Map) field.get(agentClassLoader);
//            if (packages != null) {
//                packages.clear();
//                System.err.println("Clear ClassLoader's pkg.");
//            }
//
//            Field modifersField = Field.class.getDeclaredField("modifiers");
//            modifersField.setAccessible(true);
//
//            field = ClassLoader.class.getDeclaredField("assertionLock");
//            field.setAccessible(true);
//            modifersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
//            field.set(agentClassLoader, null);
//            System.err.println("Clear ClassLoader's assertionLock");
//
//            field = ClassLoader.class.getDeclaredField("defaultDomain");
//            field.setAccessible(true);
//            modifersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
//            field.set(agentClassLoader, null);
//            System.err.println("Clear ClassLoader's defaultDomain");
//
//
//            field = SecureClassLoader.class.getDeclaredField("pdcache");
//            field.setAccessible(true);
//            Map pdcache = (Map) field.get(agentClassLoader);
//            if (pdcache != null) {
//                Field keyField = ProtectionDomain.class.getDeclaredField("key");
//                keyField.setAccessible(true);
//                for (Object pd : pdcache.values()) {
//                    System.err.println("Clear ProtectionDomain's Key");
//                    keyField.set(pd, null);
//                }
//                pdcache.clear();
//                System.err.println("Clear ClassLoader's pdCache");
//            }
//
//            ClassLoader appClassLoader = ClassLoader.getSystemClassLoader();
//            Field classesField = ClassLoader.class.getDeclaredField("classes");
//            classesField.setAccessible(true);
//            Vector classes = (Vector) classesField.get(appClassLoader);
//            classes.remove(agentClassLoader.getClass());


            // todo pdcache
            // todo ClassFilLocator $ ForClassLoader  BOOT_LOADER_PROXY
            System.err.println("release agent class loader.");
            agentClassLoader = null;

            System.gc();
            System.runFinalization();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void detachASM() {
        try {
            if (agentClass == null) {
                return;
            }
            agentClass.getDeclaredMethod("detach")
                    .invoke(null);
            System.err.println("release agent class.");
            agentClass = null;
            System.err.println("close agent class loader.");

            agentClassLoader.close();
            agentClassLoader = null;

            System.gc();
            System.runFinalization();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void attach(String args, Instrumentation instrumentation, String agentFile) throws Throwable {
        if (agentClassLoader != null) {
            return;
        }
        File basicAgent = new File(agentFile);
        String basicAgentName = basicAgent.getName();
        String namePrefix = basicAgentName.substring(0, basicAgentName.lastIndexOf('.'));

        try (InputStream in = Files.newInputStream(basicAgent.toPath())) {
            Path agentPath = Paths.get(basicAgent.getParent(), namePrefix + "-" + sequencer.incrementAndGet() + ".jar");
            Files.copy(in, agentPath);
            System.err.println("Use agent: " + agentPath.toAbsolutePath());
            agentClassLoader = new MyURLClassLoader(new URL[]{agentPath.toUri().toURL()}, null);
        }
        try {
            agentClass = Class.forName("org.example.AgentMain", true, agentClassLoader);
            agentClass.getDeclaredMethod("init", String.class, Instrumentation.class)
                    .invoke(null, args, instrumentation);
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    public static void premain(String args, Instrumentation instrumentation) throws MalformedURLException {
        String detachFile = System.getProperty("agent.detach.file");
        if (detachFile == null) {
            throw new NullPointerException("system property: [agent.detach.file]");
        }

        String agentFile = System.getProperty("agent.file");
        if (agentFile == null) {
            throw new NullPointerException("system property: [agent.file]");
        }


        if (started.compareAndSet(false, true)) {
            new Thread(() -> {
                boolean asm = Boolean.getBoolean("agent.asm.enabled");
                while (true) {
                    File detach = new File(detachFile);
                    if (detach.exists()) {
                        if (asm) {
                            detachASM();
                        } else {
                            detach();
                        }
                    }

                    File attach = new File(detach.getParent(), "attach.txt");
                    if (attach.exists()) {
                        try {

                            if (asm) {
                                attachASM(args, instrumentation, agentFile);
                            } else {
                                attach(args, instrumentation, agentFile);
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }

                    try {
                        TimeUnit.SECONDS.sleep(5L);
                    } catch (InterruptedException ignored) {
                    }
                }
            }).start();
        }

    }


}
