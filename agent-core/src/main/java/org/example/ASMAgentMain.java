package org.example;


import org.objectweb.asm.*;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;

/**
 * <h1> ASMAgentMain </h1>
 *
 * @author yuwen
 * @since 2023-04-27
 */
public class ASMAgentMain {

    static ClassFileTransformer transformer;
    static Instrumentation inst;
    public static void init(String arg, Instrumentation inst) throws Throwable {
        ASMAgentMain.inst = inst;
        IndyBootstrap.getIndyBootstrapMethod();
        transformer = new ASMClassFileTransformer();
        inst.addTransformer(transformer, true);
        Class[] classes = inst.getAllLoadedClasses();
        for (Class klass : classes) {
            if (klass != null && "org.example.InstrumentedClass".equals(klass.getName())) {
                System.err.println("Retransform InstrumentedClass");
                inst.retransformClasses(klass);
            }
        }
    }


    public static void detach() throws Throwable {
        if (transformer != null) {
            System.err.println("de-instrument");
            boolean removed = inst.removeTransformer(transformer);
            if (!removed) {
                System.err.println("Cannot Remove transformer");
                return;
            }

            Class[] classes = inst.getAllLoadedClasses();
            for (Class klass : classes) {
                if (klass != null && "org.example.InstrumentedClass".equals(klass.getName())) {
                    System.err.println("Retransform InstrumentedClass");
                    inst.retransformClasses(klass);
                }
            }

            transformer = null;
            inst = null;

            IndyPluginClassLoaderFactory.clear();
            IndyBootstrap.destroy();

            Field field = Class.forName("java.lang.IndyBootstrapDispatcher", false, null)
                    .getField("bootstrap");
            field.set(null, null);
        }
    }

    private static final class ASMClassFileTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(
                ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer) throws IllegalClassFormatException {
            if (!"org/example/InstrumentedClass".equals(className)) {
                return null;
            }

            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);


            ClassVisitor invokeDynamicVisitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (!"instrumentedMethod".equals(name)) {
                        return mv;
                    }

                    System.out.println("descriptor: " + descriptor);
                    System.out.println("signature: " + signature);

                    return new MethodVisitor(api, mv) {
                        @Override
                        public void visitCode() {

                            //Label L0
                            Label l0 = new Label();
                            super.visitLabel(l0);
                            super.visitLineNumber(7, l0);
                            Object[] constants = new Object[5];
                            constants[0] = "org.example.advices.indy.AppInstrumentedClassIndyAdvice";
                            constants[1] = 0;
                            constants[2] = Type.getType("Lorg/example/InstrumentedClass;");
                            constants[3] = "instrumentedMethod";
                            constants[4] = new Handle(
                                    Opcodes.H_INVOKESTATIC, "org/example/InstrumentedClass",
                                    "instrumentedMethod", "()V", false);
                            super.visitInvokeDynamicInsn("before", "()V",
                                    new Handle(Opcodes.H_INVOKESTATIC, "java/lang/IndyBootstrapDispatcher", "bootstrap",
                                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                                            false
                                    ), constants
                            );
                            //NOP
                            super.visitInsn(Opcodes.NOP);
                            super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);

                            Label l1 = new Label();
                            super.visitLabel(l1);
                            super.visitInsn(Opcodes.RETURN);

                            super.visitMaxs(2, 0);
                        }
                    };
                }
            };
            try {
                reader.accept(invokeDynamicVisitor, ClassReader.SKIP_DEBUG);
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
            byte[] bytes = writer.toByteArray();

            try {
                Files.write(Paths.get("./InstrumentedClass.class"), bytes, StandardOpenOption.CREATE);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return bytes;
        }
    }

}
