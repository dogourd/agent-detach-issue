package org.example;

import net.bytebuddy.jar.asm.*;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
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

    public static void init(String arg, Instrumentation inst) {
        IndyBootstrap.getIndyBootstrapMethod();
        inst.addTransformer(new ASMClassFileTransformer());
    }

    private static final class ASMClassFileTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(
                ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer) throws IllegalClassFormatException {
            if (!"org/springframework/web/servlet/FrameworkServlet".equals(className)) {
                return null;
            }

            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);


            ClassVisitor invokeDynamicVisitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (!"doGet".equals(name) || !"(Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)V".equals(descriptor)) {
                        return mv;
                    }


                    System.out.println("descriptor: " + descriptor);
                    System.out.println("signature: " + signature);

                    return new MethodVisitor(api, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            //Label L0
                            Label l0 = new Label();
                            super.visitLabel(l0);
                            super.visitLineNumber(898, l0);
                            Object[] constants = new Object[5];
                            constants[0] = "org.example.advices.indy.HttpServletIndyAdvice";
                            constants[1] = 0;
                            constants[2] = Type.getType("Lorg/springframework/web/servlet/FrameworkServlet;");
                            constants[3] = "doGet";
                            constants[4] = new Handle(
                                    Opcodes.H_INVOKEVIRTUAL, "org/springframework/web/servlet/FrameworkServlet",
                                    "doGet", "(Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)V", false);
                            super.visitInvokeDynamicInsn("before", "()V",
                                    new Handle(Opcodes.H_INVOKESTATIC, "java/lang/IndyBootstrapDispatcher", "bootstrap",
                                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                                            false
                                    ), constants
                            );
                            super.visitLabel(l0);

                            //Label L1
                            Label l1 = new Label();
                            super.visitLabel(l1);
                            super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

                            //NOP
                            super.visitInsn(Opcodes.NOP);
                            super.visitLabel(l1);

                            //Label L2
                            Label l2 = new Label();
                            super.visitLabel(l2);
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitVarInsn(Opcodes.ALOAD, 2);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/springframework/web/servlet/FrameworkServlet", "processRequest",
                                    "(Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)V", false);
                            super.visitLabel(l2);

                            //Label L3
                            Label l3 = new Label();
                            super.visitLabel(l3);
                            super.visitLineNumber(899, l3);
                            super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(l3);

                            //Label L4
                            Label l4 = new Label();
                            super.visitLabel(l4);
                            super.visitLocalVariable("this", "Lorg/springframework/web/servlet/FrameworkServlet;", null, l2, l4, 0);
                            super.visitLocalVariable("request", "Ljavax/servlet/http/HttpServletRequest;", null, l2, l4, 1);
                            super.visitLocalVariable("response", "Ljavax/servlet/http/HttpServletResponse;", null, l2, l4, 2);
                            super.visitMaxs(3, 3);
                            super.visitLabel(l4);
                        }
                    };

//                    return new MethodVisitor(api) {
//                        @Override
//                        public void visitCode() {
//                            Object[] constants = new Object[5];
//                            constants[0] = "org.example.advices.indy.HttpServletIndyAdvice";
//                            constants[1] = 0;
//                            constants[2] = Type.getType("Lorg/springframework/web/servlet/FrameworkServlet;");
//                            constants[3] = "doGet";
//                            constants[4] = new Handle(
//                                    Opcodes.H_INVOKEVIRTUAL,
//                                    "org/springframework/web/servlet/FrameworkServlet",
//                                    "doGet",
//                                    "(Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)V",
//                                    false
//                            );
//
//                            visitInvokeDynamicInsn(
//                                    "before", "()V",
//                                    new Handle(
//                                            Opcodes.H_INVOKESTATIC,
//                                            "java/lang/IndyBootstrapDispatcher",
//                                            "bootstrap",
//                                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
//                                            false
//                                    ), constants
//                            );
////                            visitVarInsn(Opcodes.ALOAD, 1);
////                            visitMaxs(3, 4);
//                            super.visitCode();
//                        }
//                    };
                }
            };
            try {
                reader.accept(invokeDynamicVisitor, ClassReader.SKIP_DEBUG);
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
            byte[] bytes = writer.toByteArray();

            try {
                Files.write(Paths.get("./FrameworkServlet.class"), bytes, StandardOpenOption.CREATE);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return bytes;
        }
    }

}
