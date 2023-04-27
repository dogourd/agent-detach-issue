package org.example.advices.indy;


import net.bytebuddy.asm.Advice;

public class HttpServletIndyAdvice {

    @Advice.OnMethodEnter(inline = false)
    public static void before(/*HttpServletRequest req, HttpServletResponse resp*/) {
        System.err.println("before HttpServletIndyAdvice");
    }
}
