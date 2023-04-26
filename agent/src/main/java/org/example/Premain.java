package org.example;


import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Premain {

    static AtomicBoolean started = new AtomicBoolean(false);
    private static Class<?> agentClass;
    private static URLClassLoader agentClassLoader;


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

            System.err.println("release agent class loader.");
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
        agentClassLoader = new MyURLClassLoader(new URL[]{new File(agentFile).toURI().toURL()}, null);
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
                while (true) {
                    File detach = new File(detachFile);
                    if (detach.exists()) {
                        detach();
                    }

                    File attach = new File(detach.getParent(), "attach.txt");
                    if (attach.exists()) {
                        try {
                            attach(args, instrumentation, agentFile);
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
