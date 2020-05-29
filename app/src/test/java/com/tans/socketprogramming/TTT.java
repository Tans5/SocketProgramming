package com.tans.socketprogramming;

import org.junit.Test;

import java.math.BigInteger;

/**
 * author: pengcheng.tan
 * date: 2020/5/19
 */
public class TTT {
    @Test
    public void t() {
        int a = -128;
        // System.out.println(Integer.toString(a,2));
        byte[] bs = BigInteger.valueOf(a).toByteArray();
        for (Byte b: bs) {

           int i = b.intValue();
           System.out.println(i);
           System.out.println(Integer.toUnsignedString(i, 2));
        }

        int b = 1;
        test1((byte) b);

        byte c = 1;
        test2(b);
    }

    public void test1(byte a) {

    }

    public void test2(int a) {

    }
}
