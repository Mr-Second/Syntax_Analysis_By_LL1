package com.Test;

import java.io.File;

public class Test {
    public static void main(String[] args)
    {
        System.out.println("用户的当前工作目录:"+System.getProperty("user.dir"));
        System.out.println(new File(System.getProperty("user.dir")+"\\src\\com\\Grammars\\grammar.txt").exists());
    }
}
